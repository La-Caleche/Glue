package fr.lacaleche.glue.web.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import fr.lacaleche.glue.web.WebCursor;
import fr.lacaleche.glue.web.WebMetrics;
import fr.lacaleche.glue.web.WebPointerEvent;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/** All engine-specific operations stay behind WebSurface's JDK/Minecraft-only API. */
public final class BrowserSession implements AutoCloseable {

    static final int MAX_MESSAGE_LENGTH = 65536;
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");

    private final Minecraft client;
    private final SurfaceOptions options;
    private final SurfaceRenderer mainRenderer = new SurfaceRenderer();
    private final SurfaceRenderer popupRenderer = new SurfaceRenderer();
    private final SurfaceMetrics metrics = new SurfaceMetrics();
    private final CursorController cursor;
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<CefView> creation;
    private CefView view;
    private Throwable failure;
    private volatile boolean closed;
    private boolean focused;
    private int width;
    private int height;
    private int fps;
    private int buttons;

    private BrowserSession(SurfaceOptions options) {
        this.client = Minecraft.getInstance();
        this.options = options;
        this.width = options.width();
        this.height = options.height();
        this.fps = options.frameRate();
        long window = this.client.getWindow().getWindow();
        this.cursor = new CursorController(window);
        long nativeWindow = switch (Platform.get()) {
            case WINDOWS -> GLFWNativeWin32.glfwGetWin32Window(window);
            case MACOSX -> GLFWNativeCocoa.glfwGetCocoaWindow(window);
            case LINUX -> GLFWNativeX11.glfwGetX11Window(window);
        };
        CefRuntime.register(this);
        this.creation = CefRuntime.start()
                .thenApplyAsync(state -> this.createBrowser(state, nativeWindow), this.client);
        this.creation.whenComplete((browser, error) -> this.client.execute(() -> {
            if (error == null || this.creation.isCancelled()) return;
            this.failure = error;
            this.ready.completeExceptionally(error);
            this.stopped.completeExceptionally(error);
            this.close();
        }));
    }

    public static BrowserSession open(SurfaceOptions options) {
        requireClientThread();
        CefRuntime.bootstrap();
        return new BrowserSession(options);
    }

    public static String runtimeStatus() {
        return CefRuntime.status();
    }

    public boolean isReady() {
        return !this.isClosed() && this.ready.isDone() && !this.ready.isCompletedExceptionally();
    }

    public boolean isLoading() {
        return !this.isReady() || this.view.loading;
    }

    public boolean isClosed() {
        return this.closed;
    }

    public String url() {
        return this.view == null ? this.options.address().toASCIIString() : this.view.url;
    }

    public String title() {
        return this.view == null ? "" : this.view.title;
    }

    public String error() {
        if (this.failure != null) return this.failure.toString();
        return this.view == null ? "" : this.view.error;
    }

    public boolean canBack() {
        return this.isReady() && this.view.canBack;
    }

    public boolean canForward() {
        return this.isReady() && this.view.canForward;
    }

    public boolean hasPopup() {
        return this.isReady() && this.view.popupVisible;
    }

    public int fpsLimit() {
        return this.fps;
    }

    public long uploadedFrames() {
        return this.metrics.uploadedFrames();
    }

    public double frameAgeMs() {
        return this.metrics.frameAgeMs();
    }

    public WebMetrics metrics() {
        return this.metrics.snapshot();
    }

    public WebCursor cursor() {
        return this.isReady() ? CursorController.fromNative(this.view.cursorType) : WebCursor.ARROW;
    }

    public WebCursor appliedCursor() {
        return this.cursor.applied();
    }

    public CompletableFuture<Void> ready() {
        return this.ready.copy();
    }

    public CompletableFuture<Void> stopped() {
        return this.stopped.copy();
    }

    public void navigate(URI address) {
        this.requireOpen();
        if (!this.isReady()) throw new IllegalStateException("Wait for native browser readiness before navigating");
        this.view.error = "";
        this.view.loadURL(address.toASCIIString());
    }

    public void back() {
        this.requireOpen();
        if (this.canBack()) this.view.goBack();
    }

    public void forward() {
        this.requireOpen();
        if (this.canForward()) this.view.goForward();
    }

    public void reload() {
        this.requireOpen();
        if (this.isReady()) this.view.reload();
    }

    public void stopLoading() {
        this.requireOpen();
        if (this.isReady()) this.view.stopLoad();
    }

    public void focus(boolean focused) {
        this.requireOpen();
        this.focused = focused;
        if (!focused) {
            this.buttons = 0;
            this.cursor.reset();
        }
        if (this.isReady()) this.view.setFocus(focused);
    }

    public void setFpsLimit(int fps) {
        this.requireOpen();
        SurfaceOptions.validateFrameRate(fps);
        this.fps = fps;
        if (this.isReady()) this.view.setWindowlessFrameRate(fps);
    }

    public void resize(int width, int height) {
        this.requireOpen();
        SurfaceOptions.validateSize(width, height);
        this.width = width;
        this.height = height;
        if (this.view != null) this.view.resize(width, height);
    }

    public void setCursorActive(boolean active) {
        requireClientThread();
        if (active && this.isReady() && this.client.isWindowActive() && this.client.getOverlay() == null
                && !this.client.mouseHandler.isMouseGrabbed()) {
            this.cursor.apply(this.cursor());
        } else {
            this.cursor.reset();
        }
    }

    public void mouse(WebPointerEvent event, int x, int y, int button, int modifiers, int clicks) {
        this.requireOpen();
        if (button < -1 || button > 2 || clicks < 1) throw new IllegalArgumentException("Invalid pointer button or click count");
        boolean buttonEvent = event == WebPointerEvent.PRESSED || event == WebPointerEvent.RELEASED;
        if (buttonEvent && button < 0) throw new IllegalArgumentException("Button events require a button");
        if (!this.isReady()) return;
        if (event == WebPointerEvent.PRESSED) this.buttons |= 1 << button;
        if (event == WebPointerEvent.RELEASED) this.buttons &= ~(1 << button);
        int type = switch (event) {
            case MOVED -> CefMouseEvent.MOUSEEVENT_MOVED;
            case EXITED -> CefMouseEvent.MOUSEEVENT_EXITED;
            case PRESSED -> CefMouseEvent.MOUSEEVENT_PRESSED;
            case RELEASED -> CefMouseEvent.MOUSEEVENT_RELEASED;
            case DRAGGED -> CefMouseEvent.MOUSEEVENT_DRAGGED;
        };
        this.view.sendCefMouseEvent(new CefMouseEvent(type, x, y, CefInput.modifiers(modifiers, this.buttons),
                CefInput.button(button), clicks));
    }

    public void wheel(int x, int y, int modifiers, int deltaX, int deltaY) {
        this.requireOpen();
        if (this.isReady()) this.view.sendCefMouseWheelEvent(new CefMouseWheelEvent(
                x, y, CefInput.modifiers(modifiers, this.buttons), deltaX, deltaY));
    }

    public void key(int key, int scanCode, int modifiers, boolean released) {
        this.requireOpen();
        if (!this.isReady()) return;
        this.view.sendCefKeyEvent(CefInput.key(key, scanCode, modifiers, released));
        if (!released && (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER)) this.character('\r', modifiers);
    }

    public void character(char character, int modifiers) {
        this.requireOpen();
        if (this.isReady()) this.view.sendCefKeyEvent(new CefKeyEvent(CefKeyEvent.KEYEVENT_CHAR,
                CefInput.modifiers(modifiers, 0), character, 0, false, character, character));
    }

    public boolean postMessage(String message) {
        this.requireOpen();
        Objects.requireNonNull(message, "message");
        if (message.length() > MAX_MESSAGE_LENGTH) throw new IllegalArgumentException("Message exceeds 65536 characters");
        if (!this.isReady() || this.view.loading || !this.view.acceptsMessages(this.view.url)) return false;
        String literal = new JsonPrimitive(message).toString();
        this.view.executeJavaScript("window.dispatchEvent(new CustomEvent('glue:web-message',{detail:" + literal + "}))",
                this.view.url, 0);
        return true;
    }

    void dispatchMessages() {
        if (this.closed || this.view == null || this.options.messages() == null) return;
        String message;
        for (int count = 0; count < 128 && !this.closed && (message = this.view.messages.poll()) != null; count++) {
            try {
                this.options.messages().accept(message);
            } catch (RuntimeException exception) {
                LOGGER.error("Web message handler failed", exception);
            }
        }
    }

    public CompletableFuture<String> evaluate(String script) {
        requireClientThread();
        Objects.requireNonNull(script, "script");
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Surface is closed"));
        return this.creation.thenCompose(browser -> browser.evaluate(script)).thenApply(result -> {
            if (result.has("exceptionDetails")) throw new IllegalStateException(result.get("exceptionDetails").toString());
            JsonElement value = result.getAsJsonObject("result").get("value");
            return value == null ? "null" : value.toString();
        });
    }

    public CompletableFuture<BufferedImage> screenshot() {
        requireClientThread();
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Surface is closed"));
        return this.creation.thenCompose(browser -> browser.createScreenshot(true));
    }

    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        this.requireOpen();
        if (this.view == null) return;
        FrameMailbox.Transfer frame = this.view.main.take();
        if (frame != null) {
            try {
                this.metrics.recordUpload(frame, this.mainRenderer.upload(frame));
            } finally {
                this.view.main.recycle(frame);
            }
        }
        this.mainRenderer.draw(graphics, x, y, width, height);
        if (this.view.popupVisible) this.drawPopup(graphics, x, y, width, height);
        this.metrics.sample(this.view.main.capturedFrames());
    }

    @Override
    public void close() {
        requireClientThread();
        if (this.closed) return;
        this.closed = true;
        if (!this.ready.isDone()) this.ready.cancel(false);
        this.cursor.close();
        this.mainRenderer.close();
        this.popupRenderer.close();
        CefRuntime.release(this);
        if (this.view == null) {
            this.creation.cancel(false);
            this.stopped.complete(null);
        } else if (this.view.disposed.isDone()) {
            this.stopped.complete(null);
        } else if (this.view.ready.isDone()) {
            this.view.close(true);
        }
    }

    private CefView createBrowser(CefRuntime.State state, long nativeWindow) {
        CefView browser = new CefView(state.client(), this.options.address().toASCIIString(), this.options.transparent(),
                this.width, this.height, nativeWindow, this.options.origin());
        this.view = browser;
        browser.disposed.whenComplete((ignored, error) -> {
            if (this.closed) {
                // Explicit close has already released client resources, even during game shutdown.
                this.stopped.complete(null);
            } else {
                this.client.execute(this::close);
            }
        });
        browser.ready.thenRun(() -> this.client.execute(() -> {
            if (this.closed || browser.disposed.isDone()) {
                if (!browser.disposed.isDone()) browser.close(true);
                return;
            }
            browser.resize(this.width, this.height);
            browser.setWindowlessFrameRate(this.fps);
            browser.setFocus(this.focused);
            this.ready.complete(null);
        }));
        browser.createImmediately();
        return browser;
    }

    private void drawPopup(GuiGraphics graphics, int x, int y, int width, int height) {
        FrameMailbox.Transfer popup = this.view.popup.take();
        if (popup != null) {
            try {
                this.popupRenderer.upload(popup);
            } finally {
                this.view.popup.recycle(popup);
            }
        }
        Rectangle bounds = this.view.popupBounds;
        graphics.enableScissor(x, y, x + width, y + height);
        try {
            this.popupRenderer.draw(graphics, x + bounds.x * width / this.width, y + bounds.y * height / this.height,
                    bounds.width * width / this.width, bounds.height * height / this.height);
        } finally {
            graphics.disableScissor();
        }
    }

    private void requireOpen() {
        requireClientThread();
        if (this.isClosed()) throw new IllegalStateException("Surface is closed");
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Web surfaces belong to the client thread");
    }
}
