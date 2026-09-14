package fr.lacaleche.jcef;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.cef.input.CefKeyEvent;
import org.cef.input.CefMouseEvent;
import org.cef.input.CefMouseWheelEvent;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeCocoa;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.system.Platform;

import java.awt.Cursor;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Client-thread owner of a native offscreen browser and its GPU textures.
 * Construction starts asynchronous CEF acquisition; close releases textures immediately and
 * {@link #stopped()} completes when acquisition is cancelled or the native browser has closed.
 */
public final class CefSurface implements AutoCloseable {

    public enum UploadMode {
        GPU_BGRA,
        CPU_RGBA
    }

    private static final String PROBE_SCRIPT = """
            (() => {
                let marker = document.getElementById('__jcef_probe');
                if (!marker) {
                    marker = document.createElement('div');
                    marker.id = '__jcef_probe';
                    document.documentElement.append(marker);
                }
                marker.style.cssText = 'all:initial;position:fixed;left:0;top:0;width:8px;height:8px;'
                    + 'z-index:2147483647;pointer-events:none;background:rgb(%d,%d,167)';
                window.jcefQuery({request:'jcef-probe:%d',onSuccess(){},onFailure(){}});
            })()
            """;

    private final SurfaceRenderer mainRenderer = new SurfaceRenderer();
    private final SurfaceRenderer popupRenderer = new SurfaceRenderer();
    private final SurfaceMetrics metrics = new SurfaceMetrics();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final CompletableFuture<CefView> creation;
    private CefView view;
    private Throwable failure;
    private boolean closed;
    private int width;
    private int height;
    private UploadMode mode = UploadMode.GPU_BGRA;
    private JsonObject lastState;
    private int probeToken;
    private int fpsLimit = 60;

    public CefSurface(String address, boolean transparent, int width, int height, String messageOrigin) {
        Objects.requireNonNull(address, "address");
        validateSize(width, height);
        this.width = width;
        this.height = height;
        Minecraft client = Minecraft.getInstance();
        long glfwWindow = client.getWindow().getWindow();
        long nativeWindow = switch (Platform.get()) {
            case WINDOWS -> GLFWNativeWin32.glfwGetWin32Window(glfwWindow);
            case MACOSX -> GLFWNativeCocoa.glfwGetCocoaWindow(glfwWindow);
            case LINUX -> GLFWNativeX11.glfwGetX11Window(glfwWindow);
        };
        this.creation = CefRuntime.start(client.gameDirectory.toPath().resolve("jcef-experiment"))
                .thenApplyAsync(state -> this.createBrowser(state, client, address, transparent, nativeWindow, messageOrigin), client);
        this.creation.whenComplete((browser, error) -> client.execute(() -> {
            if (error == null || this.creation.isCancelled()) return;
            this.failure = error;
            this.stopped.completeExceptionally(error);
        }));
    }

    public static void registerPipelines() {
        SurfaceRenderer.registerPipelines();
    }

    public boolean isReady() {
        return this.isNativeReady() && !this.closed;
    }

    public boolean isLoading() {
        return !this.isReady() || this.view.loading;
    }

    public String url() {
        return this.view == null ? "" : this.view.url;
    }

    public String title() {
        return this.view == null ? "" : this.view.title;
    }

    public boolean canBack() {
        return this.isReady() && this.view.canBack;
    }

    public boolean canForward() {
        return this.isReady() && this.view.canForward;
    }

    public String error() {
        if (this.failure != null) return this.failure.toString();
        return this.view == null ? "" : this.view.error;
    }

    public CompletableFuture<Void> stopped() {
        return this.stopped;
    }

    public long uploadedFrames() {
        return this.metrics.uploadedFrames();
    }

    public UploadMode uploadMode() {
        return this.mode;
    }

    public boolean hasPopup() {
        return this.view != null && this.view.popupVisible;
    }

    public int completedProbe() {
        return this.metrics.completedProbe();
    }

    public int fpsLimit() {
        return this.fpsLimit;
    }

    /** Latest AWT cursor identifier published by CEF; applying it belongs to the interactive host. */
    public int cursorType() {
        return this.isReady() ? this.view.cursorType : Cursor.DEFAULT_CURSOR;
    }

    public void setFpsLimit(int fps) {
        if (fps < 1 || fps > 120) throw new IllegalArgumentException("CEF frame rate must be in 1..120");
        this.fpsLimit = fps;
        if (this.isReady()) this.view.setWindowlessFrameRate(fps);
    }

    public void resize(int width, int height) {
        validateSize(width, height);
        this.width = width;
        this.height = height;
        if (!this.closed && this.view != null) this.view.resize(width, height);
    }

    public void navigate(String url) {
        if (!this.isReady()) return;
        this.view.error = "";
        this.view.loadURL(url);
    }

    public void back() {
        if (this.canBack()) this.view.goBack();
    }

    public void forward() {
        if (this.canForward()) this.view.goForward();
    }

    public void reload() {
        if (this.isReady()) this.view.reload();
    }

    public void stopLoading() {
        if (this.isReady()) this.view.stopLoad();
    }

    public void focus(boolean focused) {
        if (this.isReady()) this.view.setFocus(focused);
    }

    public void setUploadMode(UploadMode mode) {
        if (this.mode == mode) return;
        this.mode = Objects.requireNonNull(mode, "mode");
        if (this.view != null) {
            this.view.main.invalidate();
            this.view.popup.invalidate();
        }
    }

    public CompletableFuture<JsonElement> evaluate(String script) {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Surface is closed"));
        return this.creation.thenCompose(browser -> browser.evaluate(script)).thenApply(result -> {
            if (result.has("exceptionDetails")) throw new IllegalStateException(result.get("exceptionDetails").toString());
            return result.getAsJsonObject("result").get("value");
        });
    }

    /** Diagnostic native-resolution capture; unlike take(), this does not consume pending GPU damage. */
    public CompletableFuture<BufferedImage> screenshot() {
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Surface is closed"));
        return this.creation.thenCompose(browser -> browser.createScreenshot(true));
    }

    public void publish(JsonObject state) {
        if (!this.isReady() || !this.view.acceptsMessages(this.view.url) || state.equals(this.lastState)) return;
        this.lastState = state.deepCopy();
        this.view.executeJavaScript("window.receiveGameState?.(" + state + ")", this.view.url, 0);
    }

    public void drainMessages(Consumer<JsonObject> consumer) {
        if (this.closed || this.view == null) return;
        JsonObject message;
        while ((message = this.view.messages.poll()) != null) consumer.accept(message);
    }

    public void mouse(int type, int x, int y, int button, int buttons, int modifiers, int clicks) {
        if (this.isReady()) this.view.sendCefMouseEvent(new CefMouseEvent(type, x, y,
                CefInput.modifiers(modifiers, buttons), CefInput.button(button), clicks));
    }

    public void wheel(int x, int y, int modifiers, int dx, int dy) {
        if (this.isReady()) this.view.sendCefMouseWheelEvent(new CefMouseWheelEvent(x, y, CefInput.modifiers(modifiers, 0), dx, dy));
    }

    public void key(int key, int scanCode, int modifiers, boolean released) {
        if (!this.isReady()) return;
        this.view.sendCefKeyEvent(CefInput.key(key, scanCode, modifiers, released));
        if (!released && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) this.character('\r', modifiers);
    }

    public void character(char character, int modifiers) {
        if (this.isReady()) this.view.sendCefKeyEvent(new CefKeyEvent(CefKeyEvent.KEYEVENT_CHAR,
                CefInput.modifiers(modifiers, 0), character, 0, false, character, character));
    }

    /** Synthetic JS-to-pixel round trip, correlated by an actual pixel in onPaint, not by the next arbitrary frame. */
    public int probe() {
        if (!this.isReady()) return 0;
        int token = this.probeToken = this.probeToken % 65535 + 1;
        this.view.pendingProbe.set(new CefView.Probe(token, System.nanoTime()));
        String script = String.format(Locale.ROOT, PROBE_SCRIPT, token & 255, token >>> 8, token);
        this.view.executeJavaScript(script, this.view.url, 0);
        return token;
    }

    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        if (this.closed || this.view == null) return;
        FrameMailbox.Transfer frame = this.view.main.take();
        if (frame != null) {
            try {
                SurfaceRenderer.Upload timing = this.mainRenderer.upload(frame, this.mode);
                this.metrics.recordUpload(frame, timing, this.view.probePaint,
                        this.view.acknowledgedProbe, this.view.acknowledgedAt);
            } finally {
                this.view.main.recycle(frame);
            }
        }
        this.mainRenderer.draw(graphics, x, y, width, height, this.mode);
        if (this.view.popupVisible) this.drawPopup(graphics, x, y, width, height);
        this.metrics.sample(this.view.main.capturedFrames());
    }

    public Metrics metrics() {
        return this.metrics.snapshot();
    }

    public double frameAgeMs() {
        return this.metrics.frameAgeMs();
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.closed = true;
        this.mainRenderer.close();
        this.popupRenderer.close();
        if (this.view == null) {
            // Cancels only this surface's acquisition, not the process-wide CEF startup.
            this.creation.cancel(false);
            this.stopped.complete(null);
        } else if (this.isNativeReady()) {
            this.view.close(true);
        }
    }

    private CefView createBrowser(CefRuntime.State state, Minecraft client, String address,
                                  boolean transparent, long nativeWindow, String messageOrigin) {
        CefView browser = new CefView(state.client(), address, transparent, this.width, this.height,
                nativeWindow, messageOrigin);
        this.view = browser;
        browser.disposed.whenComplete((ignored, error) -> this.stopped.complete(null));
        browser.ready.thenRun(() -> client.execute(() -> {
            if (this.closed) {
                browser.close(true);
                return;
            }
            browser.resize(this.width, this.height);
            browser.setWindowlessFrameRate(this.fpsLimit);
            browser.setFocus(client.screen != null);
        }));
        browser.createImmediately();
        return browser;
    }

    private void drawPopup(GuiGraphics graphics, int x, int y, int width, int height) {
        FrameMailbox.Transfer popup = this.view.popup.take();
        if (popup != null) {
            try {
                this.popupRenderer.upload(popup, this.mode);
            } finally {
                this.view.popup.recycle(popup);
            }
        }
        Rectangle bounds = this.view.popupBounds;
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            this.popupRenderer.draw(graphics,
                    x + bounds.x * width / this.width, y + bounds.y * height / this.height,
                    bounds.width * width / this.width, bounds.height * height / this.height, this.mode);
        } finally {
            graphics.disableScissor();
        }
    }

    private boolean isNativeReady() {
        return this.view != null && this.view.ready.isDone();
    }

    private static void validateSize(int width, int height) {
        if (width < 1 || height < 1 || width > 4096 || height > 4096) {
            throw new IllegalArgumentException("Surface dimensions must be in 1..4096");
        }
    }

    public record Metrics(double paintFps, double uploadFps, double captureMs, double stagingMs, double conversionMs,
                          double uploadMs, double dirtyPercent, long coalesced, double probePaintMs, double probeUploadMs, long frames, double probeAckMs) {
    }
}
