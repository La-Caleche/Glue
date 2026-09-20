package fr.lacaleche.glue.web.internal.app;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.app.WebAppStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One serial worker per registered app. CEF/client threads only read immutable mounts and snapshots. */
public final class BundleApp implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    static final String PREFIX = "/_glue/";

    private final BundleConfig config;
    private final BundleStore store;
    private final BundleHttp http;
    private final Executor notifications;
    private final ExecutorService worker;
    private final CopyOnWriteArrayList<Consumer<WebAppStatus>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Mount> mounts = new ConcurrentHashMap<>();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final Mount embedded;
    private volatile Mount selected;
    private volatile WebAppStatus status;
    private volatile boolean closed;
    private Mount previous;
    private Mount ready;
    private WebAppStatus.Selection selection = WebAppStatus.Selection.AUTOMATIC;
    private BundleManifest channel;
    private CompletableFuture<WebAppStatus> checking;
    private boolean initialized;

    public BundleApp(BundleConfig config, Executor notifications) {
        this(config, notifications, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    BundleApp(BundleConfig config, Executor notifications, HttpClient client) {
        this.config = config;
        this.notifications = notifications;
        this.store = new BundleStore(config);
        this.http = new BundleHttp(client);
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Glue-web-app-" + config.id());
            thread.setDaemon(true);
            return thread;
        });
        WebAppStatus.Source source = config.development() ? WebAppStatus.Source.DEVELOPMENT : WebAppStatus.Source.EMBEDDED;
        this.embedded = new Mount("embedded", config.embedded(), new WebAppStatus.Release(config.version(), source, null), 0);
        this.selected = this.embedded;
        this.mounts.put(this.embedded.token(), this.embedded);
        this.status = new WebAppStatus(this.embedded.release(), null, WebAppStatus.Phase.RESTORING, this.selection, null);
    }

    public BundleConfig config() {
        return this.config;
    }

    public WebAppStatus status() {
        return this.status;
    }

    public Runnable observe(Consumer<WebAppStatus> listener) {
        this.listeners.add(listener);
        this.notifyListener(listener, this.status);
        return () -> this.listeners.remove(listener);
    }

    /** Also restores the cache on its first invocation. Concurrent checks share one operation. */
    public synchronized CompletableFuture<WebAppStatus> check() {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Web applications are stopping"));
        if (this.checking == null || this.checking.isDone()) {
            this.checking = this.submit(() -> {
                if (!this.initialized) this.initialize();
                if (this.config.channel() != null && !this.config.development()) this.update();
            });
        }
        return this.checking.copy();
    }

    public CompletableFuture<WebAppStatus> activate() {
        return this.submit(() -> {
            if (this.ready == null) throw new IllegalStateException("No compatible bundle is ready");
            this.select(this.ready, WebAppStatus.Selection.AUTOMATIC);
        });
    }

    public CompletableFuture<WebAppStatus> useEmbedded() {
        return this.submit(() -> this.select(this.embedded, WebAppStatus.Selection.EMBEDDED));
    }

    public CompletableFuture<WebAppStatus> usePrevious() {
        return this.submit(() -> {
            if (this.previous == null) throw new IllegalStateException("No previous bundle is available");
            this.select(this.previous, WebAppStatus.Selection.PINNED);
        });
    }

    public CompletableFuture<WebAppStatus> resumeUpdates() {
        return this.submit(() -> {
            Mount next = this.ready != null && this.config.activation() == WebApp.Activation.NEXT_OPEN ? this.ready : this.selected;
            this.select(next, WebAppStatus.Selection.AUTOMATIC);
            if (this.config.channel() != null && !this.config.development()) this.update();
        });
    }

    /** Captures the selection once; even a later reload continues to use this mount. */
    public Page open(URI address) {
        if (this.closed) throw new IllegalStateException("Web applications are stopping");
        return new Page(this, this.selected, address);
    }

    ResourceLocation resolve(URI address, Page page) {
        if (!this.config.origin().getHost().equals(address.getHost()) || !"https".equals(address.getScheme())
                || address.getUserInfo() != null || address.getPort() != -1 && address.getPort() != 443) return null;
        if (!address.getRawPath().startsWith(PREFIX) && page != null) address = page.resolve(address);
        String path = address.getRawPath();
        if (!path.startsWith(PREFIX)) return null;
        int slash = path.indexOf('/', PREFIX.length());
        if (slash < 0) return null;
        String token = path.substring(PREFIX.length(), slash);
        if (page != null && page.app == this && !page.mount.token().equals(token)) return null;
        Mount mount = this.mounts.get(token);
        return mount == null ? null : new ResourceLocation(mount.root(), this.config.origin() + path.substring(slash + 1));
    }

    CompletableFuture<Void> stopped() {
        return this.stopped.copy();
    }

    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;
        this.listeners.clear();
        this.http.close();
        // The final worker task releases the cache lock after the interrupted request/installation unwinds.
        this.worker.execute(() -> {
            try {
                this.store.close();
                this.stopped.complete(null);
            } catch (IOException exception) {
                LOGGER.warn("Could not close web app cache {}", this.config.id(), exception);
                this.stopped.completeExceptionally(exception);
            }
        });
        this.worker.shutdown();
    }

    private void initialize() throws IOException {
        if (this.config.development()) {
            this.initialized = true;
            return;
        }
        this.store.open();
        this.initialized = true;
        byte[] channelBytes = this.store.read("channel.json", BundleManifest.MAX_MANIFEST);
        if (channelBytes != null) {
            try {
                this.channel = BundleManifest.verify(channelBytes, this.config);
            } catch (IOException exception) {
                LOGGER.warn("Ignoring untrusted cached channel for {}: {}", this.config.id(), exception.getMessage());
            }
        }
        byte[] saved = this.store.read("selection.json", 4096);
        if (saved == null) return;
        try {
            JsonObject state = JsonParser.parseString(new String(saved, StandardCharsets.UTF_8)).getAsJsonObject();
            if (state.get("format").getAsInt() != 1) throw new IOException("Unsupported local selection format");
            this.selection = WebAppStatus.Selection.valueOf(state.get("mode").getAsString());
            this.previous = this.restoreAvailable(state.get("previous").getAsString());
            this.ready = this.restoreAvailable(state.get("ready").getAsString());
            Mount active = this.restoreAvailable(state.get("selected").getAsString());
            this.selected = this.selection == WebAppStatus.Selection.EMBEDDED || active == null ? this.embedded : active;
            if (this.ready == this.selected) this.ready = null;
            if (this.ready != null && this.selection == WebAppStatus.Selection.AUTOMATIC
                    && this.config.activation() == WebApp.Activation.NEXT_OPEN) this.select(this.ready, this.selection);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Using embedded web app {} after cache validation failed", this.config.id(), exception);
            this.publish(WebAppStatus.Phase.IDLE, exception.getMessage());
        }
    }

    private Mount restoreAvailable(String token) {
        try {
            return this.restore(token);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Ignoring unavailable cached bundle for {}: {}", this.config.id(), exception.getMessage());
            return null;
        }
    }

    private Mount restore(String token) throws IOException {
        if (token.isEmpty()) return null;
        Mount existing = this.mounts.get(token);
        if (existing != null) return existing;
        Path root = this.store.restore(token);
        BundleManifest manifest = this.store.manifest(token);
        Mount mount = mount(manifest.release(), root);
        this.mounts.put(token, mount);
        return mount;
    }

    private void update() throws IOException, InterruptedException {
        this.publish(WebAppStatus.Phase.CHECKING, null);
        byte[] bytes = this.http.get(this.config.channel(), BundleManifest.MAX_MANIFEST);
        BundleManifest candidate = BundleManifest.verify(bytes, this.config);
        if (this.channel != null && (candidate.revision() < this.channel.revision()
                || candidate.revision() == this.channel.revision() && !Arrays.equals(candidate.envelope(), this.channel.envelope()))) {
            throw new IOException("Channel revision was replayed or changed without increasing its revision");
        }
        this.store.write("channel.json", candidate.envelope());
        this.channel = candidate;
        BundleManifest.Release release = candidate.release();
        this.ready = null;
        if (release == null) {
            this.persist(this.selected, this.previous, null, this.selection);
            return;
        }
        Mount installed = this.mounts.get(BundleStore.token(release));
        if (installed == null) {
            this.publish(WebAppStatus.Phase.DOWNLOADING, null);
            byte[] archive = this.http.get(release.url(), release.size());
            this.publish(WebAppStatus.Phase.VERIFYING, null);
            installed = mount(release, this.store.install(candidate, archive));
            this.mounts.put(installed.token(), installed);
        } else {
            if (installed.archiveSize() != release.size()) throw new IOException("Cached archive size disagrees with the signed manifest");
            this.store.write("releases/" + installed.token() + "/manifest.json", candidate.envelope());
        }
        if (installed == this.selected) {
            this.persist(this.selected, this.previous, null, this.selection);
            return;
        }
        this.ready = installed;
        if (this.selection == WebAppStatus.Selection.AUTOMATIC && this.config.activation() == WebApp.Activation.NEXT_OPEN) {
            this.select(installed, this.selection);
        } else {
            this.persist(this.selected, this.previous, this.ready, this.selection);
        }
    }

    private void select(Mount next, WebAppStatus.Selection mode) throws IOException {
        Mount old = next.token().equals(this.selected.token()) ? this.previous : this.selected;
        Mount pending = next == this.ready ? null : this.ready;
        this.persist(next, old, pending, mode);
        this.previous = old;
        this.ready = pending;
        this.selection = mode;
        this.selected = next;
    }

    private void persist(Mount active, Mount old, Mount pending, WebAppStatus.Selection mode) throws IOException {
        if (!this.initialized) throw new IllegalStateException("Wait for the application cache to finish restoring");
        if (this.config.development()) return;
        JsonObject saved = new JsonObject();
        saved.addProperty("format", 1);
        saved.addProperty("mode", mode.name());
        saved.addProperty("selected", active.token());
        saved.addProperty("previous", old == null ? "" : old.token());
        saved.addProperty("ready", pending == null ? "" : pending.token());
        this.store.write("selection.json", saved.toString().getBytes(StandardCharsets.UTF_8));
    }

    private synchronized CompletableFuture<WebAppStatus> submit(Operation operation) {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Web applications are stopping"));
        return CompletableFuture.supplyAsync(() -> {
            if (this.closed) return this.status;
            try {
                operation.run();
                this.publish(this.ready == null ? WebAppStatus.Phase.IDLE : WebAppStatus.Phase.READY, null);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                this.publish(WebAppStatus.Phase.IDLE, "Application update interrupted");
            } catch (IOException | RuntimeException exception) {
                LOGGER.warn("Web app {}: {}", this.config.id(), exception.getMessage());
                this.publish(this.ready == null ? WebAppStatus.Phase.IDLE : WebAppStatus.Phase.READY, exception.getMessage());
            }
            return this.status;
        }, this.worker);
    }

    private void publish(WebAppStatus.Phase phase, String error) {
        if (this.closed) return;
        WebAppStatus snapshot = new WebAppStatus(this.selected.release(), this.ready == null ? null : this.ready.release(), phase, this.selection, error);
        this.status = snapshot;
        for (Consumer<WebAppStatus> listener : this.listeners) this.notifyListener(listener, snapshot);
    }

    private void notifyListener(Consumer<WebAppStatus> listener, WebAppStatus snapshot) {
        this.notifications.execute(() -> {
            if (!this.closed && snapshot == this.status && this.listeners.contains(listener)) {
                try {
                    listener.accept(snapshot);
                } catch (RuntimeException exception) {
                    LOGGER.error("Web app status listener failed for {}", this.config.id(), exception);
                }
            }
        });
    }

    private static Mount mount(BundleManifest.Release release, Path root) {
        return new Mount(BundleStore.token(release), root, new WebAppStatus.Release(release.version(), WebAppStatus.Source.DOWNLOADED, release.sha256()), release.size());
    }

    private record Mount(String token, Path root, WebAppStatus.Release release, int archiveSize) { }

    record ResourceLocation(Path root, String url) { }

    @FunctionalInterface
    private interface Operation {
        void run() throws IOException, InterruptedException;
    }

    /** Immutable per-surface routing, shared with CEF's IO thread. No native types cross this boundary. */
    public static final class Page {

        private final BundleApp app;
        private final Mount mount;
        private final URI address;

        private Page(BundleApp app, Mount mount, URI address) {
            this.app = app;
            this.mount = mount;
            this.address = this.resolve(address);
        }

        public URI address() {
            return this.address;
        }

        public URI resolve(URI address) {
            if (!this.app.config.origin().getHost().equals(address.getHost()) || !"https".equals(address.getScheme())) return address;
            String path = address.getRawPath();
            if (path.startsWith(PREFIX)) return address;
            return URI.create(this.app.config.origin() + PREFIX.substring(1) + this.mount.token() + "/"
                    + (path.startsWith("/") ? path.substring(1) : path)
                    + (address.getRawQuery() == null ? "" : "?" + address.getRawQuery())
                    + (address.getRawFragment() == null ? "" : "#" + address.getRawFragment()));
        }
    }
}
