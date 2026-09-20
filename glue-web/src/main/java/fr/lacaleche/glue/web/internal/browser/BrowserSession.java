package fr.lacaleche.glue.web.internal.browser;

import com.google.gson.JsonElement;
import fr.lacaleche.glue.web.WebCursor;
import fr.lacaleche.glue.web.WebMetrics;
import fr.lacaleche.glue.web.WebPointerEvent;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.internal.bridge.Bridge;
import fr.lacaleche.glue.web.internal.bridge.SlotBinding;
import fr.lacaleche.glue.web.WebSurface;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** All engine-specific operations stay behind WebSurface's JDK/Minecraft-only API. */
public final class BrowserSession implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");

    private final Minecraft client;
    private final SurfaceOptions options;
    private final Bridge bridge;
    private final SurfaceRenderer mainRenderer = new SurfaceRenderer();
    private final SurfaceRenderer popupRenderer = new SurfaceRenderer();
    private final SurfaceMetrics metrics = new SurfaceMetrics();
    private final CursorController cursor;
    private final Set<String> failedSlots = new HashSet<>();
    private final CompletableFuture<Void> stopped = new CompletableFuture<>();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<CefView> creation;
    private CefView view;
    private Throwable failure;
    private volatile boolean closed;
    private boolean focused;
    private int width;
    private int height;
    private double scale;
    private int fps;
    private int buttons;
    private List<WebSlot> slots = List.of();

    private BrowserSession(SurfaceOptions options, WebSurface owner) {
        this.client = Minecraft.getInstance();
        this.options = options;
        this.width = options.width();
        this.height = options.height();
        this.scale = options.scale();
        this.fps = options.frameRate();
        this.bridge = new Bridge(options.bridge(), owner, this::runOnClient);
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

    public static BrowserSession open(SurfaceOptions options, WebSurface owner) {
        requireClientThread();
        CefRuntime.bootstrap();
        return new BrowserSession(options, owner);
    }

    public static String runtimeStatus() {
        return CefRuntime.status();
    }

    /** True once client shutdown has closed every surface; hosts must not open new ones. */
    public static boolean isRuntimeStopping() {
        return CefRuntime.isStopping();
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

    /** A complete paint has been captured, even if no host has drawn it yet. */
    public boolean hasFrame() {
        return !this.closed && this.view != null && this.view.main.capturedFrames() > 0;
    }

    public boolean isConnected() {
        return !this.closed && this.bridge.isConnected();
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

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public double scale() {
        return this.scale;
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

    public List<WebSlot> slots() {
        return this.slots;
    }

    public CompletableFuture<Void> ready() {
        return this.ready.copy();
    }

    public CompletableFuture<Void> stopped() {
        return this.stopped.copy();
    }

    /** The host runs this when the page asks to close; hosts without a close action leave it unset. */
    public void onCloseRequest(Runnable handler) {
        requireClientThread();
        this.bridge.onCloseRequest(Objects.requireNonNull(handler, "handler"));
    }

    public boolean emit(String event, Object data) {
        this.requireOpen();
        return this.bridge.emit(event, data);
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
        if (this.focused == focused) return;
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

    public void resize(int width, int height, double scale) {
        this.requireOpen();
        SurfaceOptions.validateSize(width, height);
        SurfaceOptions.validateScale(width, height, scale);
        this.width = width;
        this.height = height;
        this.scale = scale;
        if (this.view != null) this.view.resize(width, height, scale);
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

    public CompletableFuture<String> evaluate(String script) {
        requireClientThread();
        Objects.requireNonNull(script, "script");
        if (this.closed) return CompletableFuture.failedFuture(new IllegalStateException("Surface is closed"));
        return this.creation.thenCompose(browser -> browser.evaluateByValue(script)).thenApply(result -> {
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

    /** Slots behind the page, the page and its popup, then slots above it. */
    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        this.requireOpen();
        if (this.view == null || width <= 0 || height <= 0) return;
        FrameMailbox.Transfer frame = this.view.main.take();
        if (frame != null) {
            try {
                this.metrics.recordUpload(frame, this.mainRenderer.upload(frame));
            } finally {
                this.view.main.recycle(frame);
            }
        }

        List<WebSlot> placed = this.placeSlots(x, y, width, height);
        this.renderSlots(graphics, placed, SlotBinding.Layer.BEHIND, x, y, width, height);
        this.mainRenderer.draw(graphics, x, y, width, height);
        if (this.view.popupVisible) this.drawPopup(graphics, x, y, width, height);
        this.renderSlots(graphics, placed, SlotBinding.Layer.ABOVE, x, y, width, height);
        this.slots = placed;
        this.metrics.sample(this.view.main.capturedFrames());
    }

    @Override
    public void close() {
        requireClientThread();
        if (this.closed) return;
        this.closed = true;
        if (!this.ready.isDone()) this.ready.cancel(false);
        this.bridge.close();
        this.slots = List.of();
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

    void tick() {
        if (!this.closed) this.bridge.tick();
    }

    private CefView createBrowser(CefRuntime.State state, long nativeWindow) {
        CefView browser = new CefView(state.client(), this.options.address().toASCIIString(), this.options.transparent(),
                this.width, this.height, this.scale, nativeWindow, this.bridge);
        this.view = browser;
        this.bridge.attach(browser);
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
            browser.resize(this.width, this.height, this.scale);
            browser.setWindowlessFrameRate(this.fps);
            browser.setFocus(this.focused);
            this.ready.complete(null);
        }));
        browser.createImmediately();
        return browser;
    }

    private List<WebSlot> placeSlots(int x, int y, int width, int height) {
        List<Bridge.SlotRect> reported = this.bridge.slots();
        if (reported.isEmpty()) return List.of();
        List<WebSlot> placed = new ArrayList<>(reported.size());
        for (Bridge.SlotRect slot : reported) placed.add(slot.place(x, y, width, height, this.width, this.height));
        return List.copyOf(placed);
    }

    private void renderSlots(GuiGraphics graphics, List<WebSlot> placed, SlotBinding.Layer layer,
                             int x, int y, int width, int height) {
        boolean clipped = false;
        for (WebSlot slot : placed) {
            SlotBinding binding = this.options.bridge().slots().get(slot.name());
            if (binding == null || binding.layer() != layer || slot.width() <= 0 || slot.height() <= 0) continue;
            if (!clipped) {
                graphics.enableScissor(x, y, x + width, y + height);
                clipped = true;
            }
            graphics.pose().pushMatrix();
            try {
                binding.renderer().render(graphics, slot);
            } catch (RuntimeException exception) {
                if (this.failedSlots.add(slot.name())) LOGGER.error("Web slot renderer {} failed", slot.name(), exception);
            } finally {
                graphics.pose().popMatrix();
            }
        }
        if (clipped) graphics.disableScissor();
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

    private void runOnClient(Runnable task) {
        if (this.client.isSameThread()) task.run();
        else this.client.execute(task);
    }

    private void requireOpen() {
        requireClientThread();
        if (this.isClosed()) throw new IllegalStateException("Surface is closed");
    }

    private static void requireClientThread() {
        if (!Minecraft.getInstance().isSameThread()) throw new IllegalStateException("Web surfaces belong to the client thread");
    }
}
