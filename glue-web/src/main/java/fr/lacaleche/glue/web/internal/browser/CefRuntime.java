package fr.lacaleche.glue.web.internal.browser;

import fr.lacaleche.glue.web.internal.app.AppResources;
import fr.lacaleche.glue.web.internal.bridge.Bridge;
import fr.lacaleche.glue.web.internal.host.HostTicker;

import io.github.trethore.jcefgithub.CefAppBuilder;
import io.github.trethore.jcefgithub.EnumPlatform;
import io.github.trethore.jcefgithub.MavenCefAppHandlerAdapter;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.cef.CefApp;
import org.cef.CefClient;
import org.cef.CefSettings;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefQueryCallback;
import org.cef.handler.CefDisplayHandlerAdapter;
import org.cef.handler.CefLifeSpanHandlerAdapter;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefLoadHandlerAdapter;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.network.CefRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/** One process-wide CEF runtime; the session registry and Fabric hooks belong to the client thread. */
public final class CefRuntime {

    static final String VERSION = "146.0.10.1";
    private static final long INITIALIZATION_TIMEOUT_SECONDS = 60;
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static final Set<BrowserSession> SESSIONS = new HashSet<>();
    private static final RuntimeStartup PROGRESS = new RuntimeStartup();
    private static final ExecutorService STARTUP = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Glue-web-startup");
        thread.setDaemon(true);
        return thread;
    });
    private static CompletableFuture<State> instance;
    private static volatile boolean stopping;
    private static boolean registered;

    private CefRuntime() {
    }

    public static void bootstrap() {
        if (registered) return;
        SurfaceRenderer.registerPipeline();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HostTicker.tick();
            for (BrowserSession session : List.copyOf(SESSIONS)) session.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            for (BrowserSession session : List.copyOf(SESSIONS)) {
                try {
                    session.close();
                } catch (RuntimeException exception) {
                    LOGGER.error("Could not close web surface at shutdown", exception);
                }
            }
            shutdown();
        });
        registered = true;
        start();
    }

    static void register(BrowserSession session) {
        SESSIONS.add(session);
    }

    static void release(BrowserSession session) {
        SESSIONS.remove(session);
    }

    static boolean isStopping() {
        return stopping;
    }

    static String status() {
        return PROGRESS.snapshot().description();
    }

    static RuntimeStartup.Snapshot progress() {
        return PROGRESS.snapshot();
    }

    static synchronized CompletableFuture<State> start() {
        if (stopping) return CompletableFuture.failedFuture(new IllegalStateException("Web runtime is stopping"));
        if (instance == null) {
            PROGRESS.begin();
            Path directory = FabricLoader.getInstance().getGameDir().resolve("glue-web");
            LOGGER.info("Preloading Chromium in the background");
            instance = CompletableFuture.supplyAsync(() -> initialize(directory), STARTUP);
            instance.whenComplete((state, failure) -> {
                if (failure != null && PROGRESS.fail(failure)) LOGGER.error("Web runtime preloading failed", failure);
            });
        }
        return instance;
    }

    private static synchronized void shutdown() {
        if (stopping) return;
        stopping = true;
        AppResources.stop();
        PROGRESS.stopping();
        if (instance != null) instance.thenAccept(state -> {
            state.client.dispose();
            state.app.dispose();
        });
        STARTUP.shutdown();
    }

    private static State initialize(Path base) {
        try {
            Path installation = base.toAbsolutePath().resolve(VERSION).resolve(EnumPlatform.getCurrentPlatform().getIdentifier());
            CefAppBuilder builder = new CefAppBuilder();
            builder.setInstallDir(installation.toFile());
            builder.setMirrors(List.of(
                    "https://repo.maven.apache.org/maven2/io/github/trethore/jcef-natives-{platform}/{tag}/jcef-natives-{platform}-{tag}.jar",
                    "https://github.com/trethore/jcefgithub/releases/download/{mvn_version}/jcef-natives-{platform}-{tag}.jar"));
            builder.setProgressHandler((phase, progress) -> PROGRESS.advance(switch (phase) {
                case LOCATING -> RuntimeStartup.Stage.CHECKING;
                case DOWNLOADING -> RuntimeStartup.Stage.DOWNLOADING;
                case EXTRACTING -> RuntimeStartup.Stage.EXTRACTING;
                case INSTALL -> RuntimeStartup.Stage.INSTALLING;
                case INITIALIZING, INITIALIZED -> RuntimeStartup.Stage.INITIALIZING;
            }, progress));
            CompletableFuture<Void> initialized = new CompletableFuture<>();
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
                @Override
                public void stateHasChanged(CefApp.CefAppState state) {
                    LOGGER.info("CEF state: {}", state);
                    if (state == CefApp.CefAppState.INITIALIZED) initialized.complete(null);
                    if (state == CefApp.CefAppState.TERMINATED) {
                        PROGRESS.stopped();
                        initialized.completeExceptionally(new IllegalStateException("CEF terminated during startup"));
                    }
                }
            });
            Path profile = Files.createDirectories(base.toAbsolutePath().resolve("profile"));
            CefSettings settings = builder.getCefSettings();
            settings.windowless_rendering_enabled = true;
            settings.cache_path = profile.toString();
            settings.root_cache_path = profile.toString();
            settings.log_file = base.toAbsolutePath().resolve("cef.log").toString();
            settings.log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING;
            String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
            if (!os.contains("mac")) {
                settings.resources_dir_path = installation.toString();
                settings.locales_dir_path = installation.resolve("locales").toString();
            }
            if (os.contains("linux")) settings.browser_subprocess_path = installation.resolve("jcef_helper").toString();
            builder.addJcefArgs("--disable-extensions", "--no-first-run");
            CefApp app = builder.build();
            CefClient client = app.createClient();
            CefMessageRouter router;
            try {
                router = configureClient(client);
                // JCEF reports initialization asynchronously; CEF rejects scheme factories registered before it.
                initialized.get(INITIALIZATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                AppResources.register(app);
            } catch (Exception exception) {
                client.dispose();
                app.dispose();
                throw exception;
            }
            String description = "Chromium " + app.getVersion().getChromeVersion();
            PROGRESS.ready(description);
            LOGGER.info("JCEF {} ready: {}", VERSION, description);
            return new State(app, client, router);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Web runtime startup interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Web runtime startup failed", exception);
        }
    }

    private static CefMessageRouter configureClient(CefClient client) {
        client.addLifeSpanHandler(new CefLifeSpanHandlerAdapter() {
            @Override
            public void onAfterCreated(CefBrowser browser) {
                if (browser instanceof CefView view) view.ready.complete(null);
            }

            @Override
            public boolean onBeforePopup(CefBrowser browser, CefFrame frame, String targetUrl, String targetFrameName) {
                browser.loadURL(targetUrl);
                return true;
            }
        });
        client.addLoadHandler(new CefLoadHandlerAdapter() {
            @Override
            public void onLoadStart(CefBrowser browser, CefFrame frame, CefRequest.TransitionType transition) {
                if (frame.isMain() && browser instanceof CefView view) view.documentStarted();
            }

            @Override
            public void onLoadingStateChange(CefBrowser browser, boolean loading, boolean back, boolean forward) {
                if (!(browser instanceof CefView view)) return;
                view.loading = loading;
                view.canBack = back;
                view.canForward = forward;
            }

            @Override
            public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode code, String text, String url) {
                if (frame.isMain() && browser instanceof CefView view && code != CefLoadHandler.ErrorCode.ERR_ABORTED) {
                    view.error = code + ": " + text;
                }
            }
        });
        client.addDisplayHandler(new CefDisplayHandlerAdapter() {
            @Override
            public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                if (frame.isMain() && browser instanceof CefView view) {
                    view.url = url;
                    view.error = "";
                }
            }

            @Override
            public void onTitleChange(CefBrowser browser, String title) {
                if (browser instanceof CefView view) view.title = title;
            }
        });
        CefMessageRouter router = CefMessageRouter.create(
                new CefMessageRouter.CefMessageRouterConfig("glueQuery", "glueCancel"),
                new CefMessageRouterHandlerAdapter() {
                    @Override
                    public boolean onQuery(CefBrowser browser, CefFrame frame, long id, String request,
                                           boolean persistent, CefQueryCallback callback) {
                        if (!(browser instanceof CefView view) || view.bridge == null) return false;
                        Bridge.Rejection rejection = persistent ? Bridge.PERSISTENT
                                : view.bridge.accept(frame.isMain(), frame.getURL(), view.document(), request);
                        // Replies travel as scripts; the query itself is only acknowledged here, on CEF's thread.
                        if (rejection == null) callback.success("");
                        else callback.failure(rejection.code().value(), rejection.message());
                        return true;
                    }
                });
        client.addMessageRouter(router);
        return router;
    }

    record State(CefApp app, CefClient client, CefMessageRouter router) {
    }
}
