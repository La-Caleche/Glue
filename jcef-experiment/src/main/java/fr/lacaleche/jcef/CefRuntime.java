package fr.lacaleche.jcef;

import com.google.gson.JsonParser;
import io.github.trethore.jcefgithub.CefAppBuilder;
import io.github.trethore.jcefgithub.EnumPlatform;
import io.github.trethore.jcefgithub.MavenCefAppHandlerAdapter;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns one CEF application per process; surfaces may be repeatedly opened and closed. */
public final class CefRuntime {

    public static final String VERSION = "146.0.10.1";
    private static final Logger LOGGER = LoggerFactory.getLogger("jcef-experiment");
    private static final ExecutorService STARTUP = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "JCEF-experiment-startup");
        thread.setDaemon(true);
        return thread;
    });
    private static CompletableFuture<State> instance;
    private static volatile String status = "Not started";
    private static boolean stopping;

    private CefRuntime() {
    }

    static synchronized CompletableFuture<State> start(Path directory) {
        if (stopping) return CompletableFuture.failedFuture(new IllegalStateException("CEF is stopping"));
        if (instance == null) instance = CompletableFuture.supplyAsync(() -> initialize(directory), STARTUP);
        return instance;
    }

    public static String status() { return status; }

    public static synchronized void shutdown() {
        if (stopping) return;
        stopping = true;
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
            builder.setProgressHandler((phase, progress) -> status = phase.toString());
            builder.setAppHandler(new MavenCefAppHandlerAdapter() {
                @Override
                public void stateHasChanged(CefApp.CefAppState state) {
                    LOGGER.info("CEF state: {}", state);
                }
            });
            // Keep the native installation separate from the disposable browser profile.
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
                public void onLoadingStateChange(CefBrowser browser, boolean loading, boolean back, boolean forward) {
                    if (browser instanceof CefView view) {
                        view.loading = loading;
                        view.canBack = back;
                        view.canForward = forward;
                    }
                }

                @Override
                public void onLoadError(CefBrowser browser, CefFrame frame, CefLoadHandler.ErrorCode code, String text, String url) {
                    if (frame.isMain() && browser instanceof CefView view && code != CefLoadHandler.ErrorCode.ERR_ABORTED) view.error = code + ": " + text;
                }
            });
            client.addDisplayHandler(new CefDisplayHandlerAdapter() {
                @Override
                public void onAddressChange(CefBrowser browser, CefFrame frame, String url) {
                    if (frame.isMain() && browser instanceof CefView view) { view.url = url; view.error = ""; }
                }

                @Override
                public void onTitleChange(CefBrowser browser, String title) {
                    if (browser instanceof CefView view) view.title = title;
                }

                @Override
                public boolean onConsoleMessage(CefBrowser browser, CefSettings.LogSeverity level, String message, String source, int line) {
                    if (level == CefSettings.LogSeverity.LOGSEVERITY_ERROR) LOGGER.warn("Page error: {} ({}:{})", message, source == null ? "" : source.split("[?#]", 2)[0], line);
                    return true;
                }
            });
            CefMessageRouter router = CefMessageRouter.create(new CefMessageRouter.CefMessageRouterConfig("jcefQuery", "jcefCancel"),
                    new CefMessageRouterHandlerAdapter() {
                        @Override
                        public boolean onQuery(CefBrowser browser, CefFrame frame, long id, String request, boolean persistent, CefQueryCallback callback) {
                            if (!(browser instanceof CefView view)) return false;
                            try {
                                if (request.length() > 65536) { callback.failure(1, "Message too large"); return true; }
                                if (request.startsWith("jcef-probe:")) {
                                    CefView.Probe probe = view.pendingProbe.get();
                                    int token = Integer.parseInt(request.substring("jcef-probe:".length()));
                                    if (probe != null && probe.token() == token) {
                                        view.acknowledgedAt = System.nanoTime();
                                        view.acknowledgedProbe = token;
                                    }
                                    callback.success("");
                                    return true;
                                }
                                if (!view.acceptsMessages(frame.getURL())) return false;
                                if (!view.messages.offer(JsonParser.parseString(request).getAsJsonObject())) {
                                    callback.failure(1, "Message queue full or message too large");
                                } else callback.success("");
                            } catch (RuntimeException exception) {
                                callback.failure(2, "Invalid message");
                            }
                            return true;
                        }
                    });
            client.addMessageRouter(router);
            status = "Chromium " + app.getVersion().getChromeVersion();
            LOGGER.info("JCEF {} ready: {}", VERSION, status);
            return new State(app, client, router);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("CEF startup interrupted", exception);
        } catch (Exception exception) {
            status = exception.toString();
            throw new IllegalStateException("CEF startup failed", exception);
        }
    }

    record State(CefApp app, CefClient client, CefMessageRouter router) {
    }
}
