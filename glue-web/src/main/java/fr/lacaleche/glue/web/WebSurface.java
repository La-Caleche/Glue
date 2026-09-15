package fr.lacaleche.glue.web;

import fr.lacaleche.glue.web.internal.BrowserSession;
import fr.lacaleche.glue.web.internal.SurfaceOptions;
import fr.lacaleche.glue.web.internal.WebOrigin;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * An owned offscreen browser. All instance methods belong to Minecraft's client thread;
 * returned futures may be observed from other threads.
 * The host closes each surface; Fabric also closes remaining surfaces at client shutdown.
 * Native acquisition is asynchronous and never blocks the render thread.
 */
public final class WebSurface implements AutoCloseable {

    public static final int MAX_DIMENSION = 4096;

    private final BrowserSession session;

    private WebSurface(SurfaceOptions options) {
        this.session = BrowserSession.open(options);
    }

    public static Builder builder(URI address) {
        return new Builder(address);
    }

    /** Runtime installation/startup status, shared by all surfaces in this process. */
    public static String runtimeStatus() {
        return BrowserSession.runtimeStatus();
    }

    public boolean isReady() {
        return this.session.isReady();
    }

    public boolean isLoading() {
        return this.session.isLoading();
    }

    public boolean isClosed() {
        return this.session.isClosed();
    }

    public String url() {
        return this.session.url();
    }

    public String title() {
        return this.session.title();
    }

    public String error() {
        return this.session.error();
    }

    public boolean canBack() {
        return this.session.canBack();
    }

    public boolean canForward() {
        return this.session.canForward();
    }

    public boolean hasPopup() {
        return this.session.hasPopup();
    }

    public int fpsLimit() {
        return this.session.fpsLimit();
    }

    public long uploadedFrames() {
        return this.session.uploadedFrames();
    }

    public double frameAgeMs() {
        return this.session.frameAgeMs();
    }

    public WebMetrics metrics() {
        return this.session.metrics();
    }

    public WebCursor cursor() {
        return this.session.cursor();
    }

    public WebCursor appliedCursor() {
        return this.session.appliedCursor();
    }

    /** Native browser creation, not document load completion. The returned future is a defensive copy. */
    public CompletableFuture<Void> ready() {
        return this.session.ready();
    }

    /** Completes on cancellation before acquisition or native disposal; never join on the client thread. */
    public CompletableFuture<Void> stopped() {
        return this.session.stopped();
    }

    /** Requires native readiness; navigation after close is rejected. */
    public void navigate(URI address) {
        this.session.navigate(SurfaceOptions.address(address));
    }

    public void back() {
        this.session.back();
    }

    public void forward() {
        this.session.forward();
    }

    public void reload() {
        this.session.reload();
    }

    public void stopLoading() {
        this.session.stopLoading();
    }

    public void setFocused(boolean focused) {
        this.session.focus(focused);
    }

    public void setFpsLimit(int fps) {
        this.session.setFpsLimit(fps);
    }

    /** Browser pixels are independent of the GUI-space destination passed to draw. */
    public void resize(int width, int height) {
        this.session.resize(width, height);
    }

    /** Draws the latest image and popup in GUI coordinates, preserving the host's scissor stack. */
    public void draw(GuiGraphics graphics, int x, int y, int width, int height) {
        this.session.draw(graphics, x, y, width, height);
    }

    /**
     * Claims/releases the window cursor for this surface. The host supplies hover/drag ownership;
     * inactive windows, grabbed mouse and Minecraft loading overlays always release it.
     */
    public void setCursorActive(boolean active) {
        this.session.setCursorActive(active);
    }

    /** Button is GLFW 0/1/2, or -1 for move/exit. Modifiers use GLFW modifier bits. */
    public void mouse(WebPointerEvent event, int x, int y, int button, int modifiers, int clicks) {
        this.session.mouse(Objects.requireNonNull(event, "event"), x, y, button, modifiers, clicks);
    }

    /** Wheel deltas use Chromium's 120-unit wheel notches. Coordinates are browser pixels. */
    public void wheel(int x, int y, int modifiers, int deltaX, int deltaY) {
        this.session.wheel(x, y, modifiers, deltaX, deltaY);
    }

    /** GLFW key/scan codes and modifier bits; committed text is supplied separately through character. */
    public void keyPressed(int key, int scanCode, int modifiers) {
        this.session.key(key, scanCode, modifiers, false);
    }

    public void keyReleased(int key, int scanCode, int modifiers) {
        this.session.key(key, scanCode, modifiers, true);
    }

    public void character(char character, int modifiers) {
        this.session.character(character, modifiers);
    }

    /**
     * Sends a string as the detail of a window 'glue:web-message' CustomEvent. Returns false while
     * loading or outside the configured message origin. Closed surfaces reject new messages.
     * The page owns its event listener.
     */
    public boolean postMessage(String message) {
        return this.session.postMessage(message);
    }

    /** Evaluates JavaScript and returns JSON-encoded by-value output ("null" for undefined). */
    public CompletableFuture<String> evaluate(String script) {
        return this.session.evaluate(script);
    }

    /** Owned native-resolution CPU image, including the popup; fails until a frame exists. */
    public CompletableFuture<BufferedImage> screenshot() {
        return this.session.screenshot();
    }

    /** Releases GPU/cursor resources immediately and requests asynchronous native disposal. Idempotent. */
    @Override
    public void close() {
        this.session.close();
    }

    public static final class Builder {

        private final URI address;
        private int width = 800;
        private int height = 600;
        private int fps = 60;
        private boolean transparent = true;
        private WebOrigin origin;
        private Consumer<String> messages;

        private Builder(URI address) {
            this.address = SurfaceOptions.address(address);
        }

        public Builder size(int width, int height) {
            SurfaceOptions.validateSize(width, height);
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder frameRate(int fps) {
            SurfaceOptions.validateFrameRate(fps);
            this.fps = fps;
            return this;
        }

        public Builder transparent(boolean transparent) {
            this.transparent = transparent;
            return this;
        }

        /** Enables window.glueQuery only for this HTTP(S) origin; delivery runs on the client tick. */
        public Builder onMessage(URI origin, Consumer<String> handler) {
            this.origin = WebOrigin.from(origin);
            this.messages = Objects.requireNonNull(handler, "handler");
            return this;
        }

        /** Opens a new independent surface using a snapshot of these options. */
        public WebSurface open() {
            return new WebSurface(new SurfaceOptions(this.address, this.width, this.height, this.fps,
                    this.transparent, this.origin, this.messages));
        }
    }
}
