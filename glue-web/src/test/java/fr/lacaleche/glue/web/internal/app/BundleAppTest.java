package fr.lacaleche.glue.web.internal.app;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.app.WebAppStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundleAppTest {

    @TempDir
    static Path certificates;
    private static SSLContext tls;

    @TempDir
    Path directory;
    private final List<BundleApp> apps = new ArrayList<>();
    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private final AtomicInteger checks = new AtomicInteger();
    private final CountDownLatch requested = new CountDownLatch(1);
    private volatile CountDownLatch gate = new CountDownLatch(0);
    private HttpsServer server;
    private ExecutorService serverThreads;
    private KeyPair signing;
    private URI channel;
    private Path embedded;

    @BeforeAll
    static void createLocalCertificate() throws Exception {
        Path keytool = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool");
        Path store = certificates.resolve("server.p12");
        Process process = new ProcessBuilder(keytool.toString(), "-genkeypair", "-alias", "fixture", "-keyalg", "EC",
                "-groupname", "secp256r1", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-validity", "2", "-storetype", "PKCS12", "-keystore", store.toString(), "-storepass", "fixture-password", "-noprompt")
                .redirectErrorStream(true).start();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "keytool must finish");
        assertEquals(0, process.exitValue(), new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        KeyStore keys = KeyStore.getInstance("PKCS12");
        try (InputStream input = Files.newInputStream(store)) {
            keys.load(input, "fixture-password".toCharArray());
        }
        KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagers.init(keys, "fixture-password".toCharArray());
        TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trust.init(keys);
        tls = SSLContext.getInstance("TLS");
        tls.init(keyManagers.getKeyManagers(), trust.getTrustManagers(), null);
    }

    @BeforeEach
    void startServer() throws Exception {
        this.signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        this.embedded = Files.createDirectory(this.directory.resolve("embedded"));
        Files.writeString(this.embedded.resolve("index.html"), "embedded");
        Files.writeString(this.embedded.resolve("lazy.js"), "embedded module");
        this.server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        this.server.setHttpsConfigurator(new HttpsConfigurator(tls));
        this.serverThreads = Executors.newVirtualThreadPerTaskExecutor();
        this.server.setExecutor(this.serverThreads);
        this.server.createContext("/", exchange -> {
            try (exchange) {
                if (exchange.getRequestURI().getPath().equals("/stable.json")) {
                    this.checks.incrementAndGet();
                    this.requested.countDown();
                    if (!this.gate.await(10, TimeUnit.SECONDS)) throw new IOException("Fixture gate timed out");
                }
                byte[] bytes = this.files.get(exchange.getRequestURI().getPath());
                if (bytes == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
        });
        this.server.start();
        this.channel = URI.create("https://127.0.0.1:" + this.server.getAddress().getPort() + "/stable.json");
    }

    @AfterEach
    void stopServer() throws Exception {
        this.gate.countDown();
        for (BundleApp app : this.apps) {
            app.close();
            app.stopped().get(10, TimeUnit.SECONDS);
        }
        this.server.stop(0);
        this.serverThreads.shutdownNow();
    }

    @Test
    void openingNeverWaitsForNetworkAndConcurrentChecksShareOneDownload() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new", "lazy.js", "new module"));
        this.gate = new CountDownLatch(1);
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        BundleApp.Page old = app.open(app.config().origin().resolve("index.html"));
        CompletableFuture<WebAppStatus> first = app.check();
        assertTrue(this.requested.await(5, TimeUnit.SECONDS));
        CompletableFuture<WebAppStatus> second = app.check();
        assertFalse(first.isDone());
        assertEquals("embedded", this.read(app, old, old.address()));
        this.gate.countDown();
        assertEquals("1.1.0", first.get(10, TimeUnit.SECONDS).selected().version());
        assertEquals(first.get(), second.get());
        assertEquals(1, this.checks.get());
        BundleApp.Page next = app.open(app.config().origin().resolve("index.html"));
        assertEquals("new", this.read(app, next, next.address()));
        assertEquals("embedded module", this.read(app, old, old.address().resolve("lazy.js")));
        assertEquals("new module", this.read(app, next, next.address().resolve("lazy.js")));
        assertEquals("embedded module", this.read(app, old, app.config().origin().resolve("lazy.js")));
        assertNull(app.resolve(next.address(), old), "An old surface cannot cross into a new bundle");
    }

    @Test
    void manualActivationAndRollbackSelectionsSurviveRestarts() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        BundleApp app = this.app(WebApp.Activation.MANUAL);
        WebAppStatus pending = app.check().get(10, TimeUnit.SECONDS);
        assertEquals("1.0.0", pending.selected().version());
        assertEquals("1.1.0", pending.ready().version());
        assertEquals("1.1.0", app.activate().get().selected().version());
        assertEquals(WebAppStatus.Selection.PINNED, app.usePrevious().get().selection());
        assertEquals("1.0.0", app.check().get().selected().version());
        app.close();
        app.stopped().get();
        BundleApp restarted = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.0.0", restarted.check().get().selected().version());
        assertEquals("1.1.0", restarted.resumeUpdates().get().selected().version());
        assertEquals(WebAppStatus.Selection.EMBEDDED, restarted.useEmbedded().get().selection());
        assertEquals("1.0.0", restarted.check().get().selected().version());
    }

    @Test
    void aNewChannelRevisionCanRollBackWhileAnOldRevisionIsRejected() throws Exception {
        this.publish(10, "1.2.0", "editor/1", Map.of("index.html", "second"));
        byte[] oldChannel = this.files.get("/stable.json");
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.2.0", app.check().get().selected().version());
        this.publish(11, "1.1.0", "editor/1", Map.of("index.html", "first"));
        assertEquals("1.1.0", app.check().get().selected().version());
        this.files.put("/stable.json", oldChannel);
        WebAppStatus rejected = app.check().get();
        assertEquals("1.1.0", rejected.selected().version());
        assertTrue(rejected.error().contains("revision"));
    }

    @Test
    void newerEmbeddedVersionsAndDifferentContractsExcludeCachedBundles() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.1.0", app.check().get().selected().version());
        app.close();
        app.stopped().get();
        BundleApp newer = this.app(WebApp.Activation.NEXT_OPEN, "2.0.0", "editor/2");
        assertEquals("2.0.0", newer.check().get().selected().version());
        assertNull(newer.status().ready());
    }

    @Test
    void signedCatalogSelectsOnlyTheExactBridgeContract() throws Exception {
        JsonObject catalog = this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "v1"));
        JsonObject incompatible = catalog.getAsJsonArray("releases").get(0).getAsJsonObject().deepCopy();
        incompatible.addProperty("contract", "editor/2");
        incompatible.addProperty("version", "9.0.0");
        catalog.getAsJsonArray("releases").add(incompatible);
        this.sign(catalog);
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.1.0", app.check().get().selected().version());
    }

    @Test
    void aPreparedCacheCanActivateOfflineWhenTheConfiguredPolicyChanges() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        BundleApp manual = this.app(WebApp.Activation.MANUAL);
        assertNotNull(manual.check().get().ready());
        manual.close();
        manual.stopped().get();
        this.files.clear();
        BundleApp automatic = this.app(WebApp.Activation.NEXT_OPEN);
        WebAppStatus status = automatic.check().get();
        assertEquals("1.1.0", status.selected().version());
        assertNotNull(status.error());
    }

    @Test
    void anOversizedHttpBodyIsRejectedBeforeExtraction() throws Exception {
        JsonObject catalog = this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        catalog.getAsJsonArray("releases").get(0).getAsJsonObject().addProperty("size", 1);
        this.sign(catalog);
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        assertNotNull(app.check().get().error());
        assertEquals(WebAppStatus.Source.EMBEDDED, app.status().selected().source());
        try (Stream<Path> paths = Files.list(this.directory.resolve("cache/releases"))) {
            assertEquals(0, paths.count());
        }
    }

    @Test
    void republishingIdenticalContentUpdatesItsVersionWithoutReplacingOpenFiles() throws Exception {
        JsonObject catalog = this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "same content"));
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.1.0", app.check().get().selected().version());
        BundleApp.Page open = app.open(app.config().origin().resolve("index.html"));
        catalog.addProperty("revision", 2);
        catalog.getAsJsonArray("releases").get(0).getAsJsonObject().addProperty("version", "1.2.0");
        this.sign(catalog);
        assertEquals("1.2.0", app.check().get().selected().version());
        assertEquals("same content", this.read(app, open, open.address()));
        app.close();
        app.stopped().get();
        this.files.clear();
        assertEquals("1.2.0", this.app(WebApp.Activation.NEXT_OPEN).check().get().selected().version());
    }

    @Test
    void forgedSignaturesAndWrongApplicationOrChannelCannotReplaceEmbeddedPages() throws Exception {
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        JsonObject catalog = this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "bad"));
        catalog.addProperty("app", "other:editor");
        this.sign(catalog);
        assertNotNull(app.check().get().error());
        catalog.addProperty("app", "demo:editor");
        catalog.addProperty("channel", "https://other.example/stable.json");
        this.sign(catalog);
        assertNotNull(app.check().get().error());
        catalog.addProperty("channel", this.channel.toString());
        KeyPair accepted = this.signing;
        this.signing = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        this.sign(catalog);
        this.signing = accepted;
        assertNotNull(app.check().get().error());
        assertEquals(WebAppStatus.Source.EMBEDDED, app.status().selected().source());
    }

    @Test
    void interruptedOrTamperedArchivesKeepThePreviouslySelectedRelease() throws Exception {
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "good"));
        assertNull(app.check().get().error());
        JsonObject manifest = this.publish(2, "1.2.0", "editor/1", Map.of("index.html", "bad"));
        String path = URI.create(manifest.getAsJsonArray("releases").get(0).getAsJsonObject().get("url").getAsString()).getPath();
        this.files.put(path, new byte[] {1, 2, 3});
        assertNotNull(app.check().get().error());
        assertEquals("1.1.0", app.status().selected().version());
        this.files.clear();
        app.close();
        app.stopped().get();
        BundleApp offline = this.app(WebApp.Activation.NEXT_OPEN);
        assertEquals("1.1.0", offline.check().get().selected().version());
        assertNotNull(offline.status().error());
    }

    @Test
    void restorationRevalidatesArchivesAndIgnoresTamperedExtractedFiles() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "signed"));
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        app.check().get();
        String token = app.open(app.config().origin()).address().getPath().split("/")[2];
        app.close();
        app.stopped().get();
        Path release = this.directory.resolve("cache/releases/" + token);
        Files.writeString(release.resolve("web/index.html"), "tampered");
        this.files.clear();
        BundleApp restored = this.app(WebApp.Activation.NEXT_OPEN);
        restored.check().get();
        BundleApp.Page page = restored.open(restored.config().origin().resolve("index.html"));
        assertEquals("signed", this.read(restored, page, page.address()));
        restored.close();
        restored.stopped().get();
        Files.write(release.resolve("bundle.zip"), new byte[] {0});
        assertEquals(WebAppStatus.Source.EMBEDDED, this.app(WebApp.Activation.NEXT_OPEN).check().get().selected().source());
    }

    @Test
    void traversalCaseCollisionsAndZipBombsAreRejectedBeforeInstallation() throws Exception {
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        List<Map<String, String>> archives = List.of(
                Map.of("index.html", "ok", "../escaped", "bad"),
                Map.of("index.html", "ok", "C:/escaped", "bad"),
                Map.of("index.html", "ok", "assets/", "directory data must not bypass extraction limits"),
                Map.of("index.html", "ok", "assets/a.js", "a", "ASSETS/b.js", "b"),
                Map.of("index.html", "ok", "large.txt", "a".repeat(32 * 1024 * 1024 + 1)));
        int revision = 0;
        for (Map<String, String> archive : archives) {
            this.publish(++revision, "1.1.0", "editor/1", archive);
            assertNotNull(app.check().get(15, TimeUnit.SECONDS).error());
            assertEquals(WebAppStatus.Source.EMBEDDED, app.status().selected().source());
        }
        assertFalse(Files.exists(this.directory.resolve("cache/escaped")));
    }

    @Test
    void cacheOwnershipPreventsTwoClientsFromMutatingTheSameInstallation() throws Exception {
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        assertNull(this.app(WebApp.Activation.NEXT_OPEN).check().get().error());
        BundleApp competing = this.app(WebApp.Activation.NEXT_OPEN);
        assertTrue(competing.check().get().error().contains("already in use"));
        assertEquals(WebAppStatus.Source.EMBEDDED, competing.status().selected().source());
    }

    @Test
    void shutdownCancelsAnInFlightRequestAndReleasesCacheOwnership() throws Exception {
        this.gate = new CountDownLatch(1);
        BundleApp app = this.app(WebApp.Activation.NEXT_OPEN);
        CompletableFuture<WebAppStatus> checking = app.check();
        assertTrue(this.requested.await(5, TimeUnit.SECONDS));
        app.close();
        app.stopped().get(5, TimeUnit.SECONDS);
        assertTrue(checking.isDone());
        this.gate.countDown();
        this.publish(1, "1.1.0", "editor/1", Map.of("index.html", "new"));
        assertNull(this.app(WebApp.Activation.NEXT_OPEN).check().get(5, TimeUnit.SECONDS).error());
    }

    private BundleApp app(WebApp.Activation activation) {
        return this.app(activation, "1.0.0", "editor/1");
    }

    private BundleApp app(WebApp.Activation activation, String version, String contract) {
        BundleConfig config = new BundleConfig("demo:editor", URI.create("https://editor.demo.glue/"), this.embedded,
                version, contract, this.channel, Map.of("release", this.signing.getPublic()), activation, this.directory.resolve("cache"), false);
        BundleApp app = new BundleApp(config, Runnable::run, HttpClient.newBuilder().sslContext(tls).build());
        this.apps.add(app);
        return app;
    }

    private JsonObject publish(long revision, String version, String contract, Map<String, String> contents) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> file : contents.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        byte[] archive = bytes.toByteArray();
        String digest = BundleStore.digest(archive);
        this.files.put("/" + digest + ".zip", archive);
        JsonObject release = new JsonObject();
        release.addProperty("version", version);
        release.addProperty("contract", contract);
        release.addProperty("url", this.channel.resolve(digest + ".zip").toString());
        release.addProperty("size", archive.length);
        release.addProperty("sha256", digest);
        JsonArray releases = new JsonArray();
        releases.add(release);
        JsonObject manifest = new JsonObject();
        manifest.addProperty("format", 1);
        manifest.addProperty("app", "demo:editor");
        manifest.addProperty("channel", this.channel.toString());
        manifest.addProperty("revision", revision);
        manifest.add("releases", releases);
        this.sign(manifest);
        return manifest;
    }

    private void sign(JsonObject manifest) throws Exception {
        byte[] payload = manifest.toString().getBytes(StandardCharsets.UTF_8);
        Signature signature = Signature.getInstance("Ed25519");
        signature.initSign(this.signing.getPrivate());
        signature.update(payload);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("format", 1);
        envelope.addProperty("keyId", "release");
        envelope.addProperty("payload", Base64.getEncoder().encodeToString(payload));
        envelope.addProperty("signature", Base64.getEncoder().encodeToString(signature.sign()));
        this.files.put("/stable.json", envelope.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String read(BundleApp app, BundleApp.Page page, URI address) throws IOException {
        BundleApp.ResourceLocation location = app.resolve(address, page);
        assertNotNull(location);
        AppFiles.Response response = AppFiles.respond(location.root(), "GET", location.url());
        assertEquals(200, response.status());
        return new String(response.body(), StandardCharsets.UTF_8);
    }
}
