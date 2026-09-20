package fr.lacaleche.glue.web.internal.app;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.cef.CefApp;
import org.cef.callback.CefCallback;
import org.cef.callback.CefResourceReadCallback;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Serves every loaded mod's {@code assets/<mod>/web/} directory from its own
 * {@code https://<mod>.glue/} origin, without a network server.
 */
public final class AppResources {

    /** Development override: {@code -Dglue.web.source.<mod>=<directory>} serves that directory instead. */
    public static final String SOURCE_PROPERTY = "glue.web.source.";
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static final Map<String, BundleApp> APPS = new ConcurrentHashMap<>();
    private static CefApp runtime;
    private static boolean stopping;

    private AppResources() {
    }

    public static URI origin(String modId) {
        return URI.create("https://" + AppFiles.host(modId) + "/");
    }

    public static Optional<Path> root(String modId) {
        String override = System.getProperty(SOURCE_PROPERTY + modId);
        if (override != null && !override.isBlank()) {
            Path directory = Path.of(override).toAbsolutePath().normalize();
            return Files.isDirectory(directory) ? Optional.of(directory) : Optional.empty();
        }
        return FabricLoader.getInstance().getModContainer(modId)
                .flatMap(mod -> mod.findPath("assets/" + modId + "/web"))
                .filter(Files::isDirectory);
    }

    public static synchronized BundleApp add(BundleConfig config, Executor notifications) {
        if (stopping) throw new IllegalStateException("Web applications are stopping");
        String host = config.origin().getHost();
        if (APPS.containsKey(host)) throw new IllegalStateException("Web application is already registered: " + config.id());
        BundleApp bundles = new BundleApp(config, notifications);
        APPS.put(host, bundles);
        if (runtime != null && !registerBundle(runtime, bundles)) {
            APPS.remove(host);
            bundles.close();
            throw new IllegalStateException("Could not register resources for " + config.id());
        }
        bundles.check();
        return bundles;
    }

    /** Null for ordinary URLs and the original unversioned mod applications. */
    public static BundleApp.Page open(URI address) {
        BundleApp app = address.getHost() == null ? null : APPS.get(address.getHost());
        return app == null || !"https".equals(address.getScheme()) ? null : app.open(address);
    }

    public static synchronized void stop() {
        stopping = true;
        APPS.values().forEach(BundleApp::close);
    }

    public static synchronized void register(CefApp app) {
        Map<String, String> served = new LinkedHashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
            String id = mod.getMetadata().getId();
            Optional<Path> root = root(id);
            if (root.isEmpty()) {
                String override = System.getProperty(SOURCE_PROPERTY + id);
                if (override != null) LOGGER.warn("Web source override for {} is not a directory: {}", id, override);
                continue;
            }

            String host;
            try {
                host = AppFiles.host(id);
            } catch (IllegalArgumentException exception) {
                LOGGER.warn("Web resources are not served: {}", exception.getMessage());
                continue;
            }
            String previous = served.putIfAbsent(host, id);
            if (previous != null) {
                LOGGER.warn("Web host {} already serves {}; {} is skipped", host, previous, id);
                continue;
            }

            Path directory = root.get();
            if (!app.registerSchemeHandlerFactory("https", host, (browser, frame, scheme, request) -> new Resource(directory))) {
                LOGGER.warn("Could not register web resources for {}", id);
                served.remove(host);
            }
        }
        for (BundleApp bundles : APPS.values()) {
            if (!registerBundle(app, bundles)) throw new IllegalStateException("Could not register " + bundles.config().id());
        }
        runtime = app;
        LOGGER.info("Serving web resources for {}", served.values());
    }

    private static boolean registerBundle(CefApp app, BundleApp bundles) {
        return app.registerSchemeHandlerFactory("https", bundles.config().origin().getHost(), (browser, frame, scheme, request) -> {
            BundleApp.Page page = browser instanceof PinnedBrowser pinned ? pinned.appPage() : null;
            BundleApp.ResourceLocation resource = bundles.resolve(URI.create(request.getURL()), page);
            return resource == null ? new Resource(null, null, true) : new Resource(resource.root(), resource.url(), true);
        });
    }

    /** Implemented by the private browser view; only immutable routing crosses the CEF thread boundary. */
    public interface PinnedBrowser {
        BundleApp.Page appPage();
    }

    /** One request. CEF calls it sequentially on its IO thread; the whole file is read in open. */
    private static final class Resource extends CefResourceHandlerAdapter {

        private final Path root;
        private final String resourceUrl;
        private final boolean managed;
        private AppFiles.Response response = AppFiles.Response.status(500);
        private int offset;

        Resource(Path root) {
            this(root, null, false);
        }

        Resource(Path root, String resourceUrl, boolean managed) {
            this.root = root;
            this.resourceUrl = resourceUrl;
            this.managed = managed;
        }

        @Override
        public boolean open(CefRequest request, BoolRef handleRequest, CefCallback callback) {
            handleRequest.set(true);
            try {
                Map<String, String> headers = new HashMap<>();
                request.getHeaderMap(headers);
                if (this.managed && headers.keySet().stream().anyMatch("Service-Worker"::equalsIgnoreCase)) {
                    this.response = AppFiles.Response.status(403);
                } else {
                    this.response = this.root == null ? AppFiles.Response.status(404)
                            : AppFiles.respond(this.root, request.getMethod(), this.resourceUrl == null ? request.getURL() : this.resourceUrl);
                }
            } catch (IOException | RuntimeException exception) {
                LOGGER.error("Could not serve {}", request.getURL(), exception);
            }
            return true;
        }

        @Override
        public void getResponseHeaders(CefResponse target, IntRef length, StringRef redirect) {
            target.setStatus(this.response.status());
            target.setStatusText(AppFiles.reason(this.response.status()));
            target.setMimeType(this.response.mimeType());
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", this.managed ? "no-store" : "no-cache");
            // Pages from any trusted origin import the shared bridge module.
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("X-Content-Type-Options", "nosniff");
            target.setHeaderMap(headers);
            length.set(this.response.body().length);
        }

        @Override
        public boolean read(byte[] output, int bytesToRead, IntRef bytesRead, CefResourceReadCallback callback) {
            byte[] body = this.response.body();
            int count = Math.min(bytesToRead, body.length - this.offset);
            if (count <= 0) {
                bytesRead.set(0);
                return false;
            }
            System.arraycopy(body, this.offset, output, 0, count);
            this.offset += count;
            bytesRead.set(count);
            return true;
        }
    }
}
