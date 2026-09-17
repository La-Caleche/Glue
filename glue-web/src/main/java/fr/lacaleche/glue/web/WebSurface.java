package fr.lacaleche.glue.web;

import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.internal.browser.BrowserSession;
import fr.lacaleche.glue.web.internal.browser.SurfaceOptions;
import net.minecraft.client.gui.GuiGraphics;

import java.awt.image.BufferedImage;
import java.net.URI;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * An owned offscreen browser. All instance methods belong to Minecraft's client thread;
 * returned futures may be observed from other threads.
 *
 * <p>Sizes are CSS pixels. The scale is the page's device pixel ratio, so the browser renders
 * {@code width * scale} by {@code height * scale} pixels. Hosts use the GUI scale, which makes one CSS
 * pixel one GUI pixel. The owner closes each surface; Fabric also closes remaining surfaces at client
 * shutdown. Native acquisition is asynchronous and never blocks the render thread.</p>
 */
public final class WebSurface implements AutoCloseable {

    /** Largest browser size in each direction, before and after scaling. */
    public static final int MAX_DIMENSION = 4096;

    private final BrowserSession session;

    private WebSurface(SurfaceOptions options) {
        this.session = BrowserSession.open(options, this);
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

    /** Whether a complete paint has arrived, even before the first draw. */
    public boolean hasFrame() {
        return this.session.hasFrame();
    }

    /** Handles the trusted page's close request on the client thread; unset hosts refuse it. */
    public void onCloseRequest(Runnable handler) {
        this.session.onCloseRequest(handler);
    }

    public boolean isLoading() {
        return this.session.isLoading();
    }

    public boolean isClosed() {
        return this.session.isClosed();
    }

    /** Whether the current document imported the bridge and was accepted by this surface. */
    public boolean isConnected() {
        return this.session.isConnected();
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

    /** Page width in CSS pixels. */
    public int width() {
        return this.session.width();
    }

    /** Page height in CSS pixels. */
    public int height() {
        return this.session.height();
    }

    public double scale() {
        return this.session.scale();
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

    /** Slots the page reported, positioned by the latest {@link #draw}; hosts use them for hit testing. */
    public List<WebSlot> slots() {
        return this.session.slots();
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

    /** Changes the page size in CSS pixels, keeping the scale. */
    public void resize(int width, int height) {
        this.session.resize(width, height, this.session.scale());
    }

    /** Changes the page size in CSS pixels and its device pixel ratio. */
    public void resize(int width, int height, double scale) {
        this.session.resize(width, height, scale);
    }

    /**
     * Draws the latest image, its popup and native slots in GUI coordinates, preserving the host's
     * scissor stack. The GUI rectangle is independent of the page size.
     */
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

    /** Coordinates are CSS pixels. Button is GLFW 0/1/2, or -1 for move/exit. Modifiers use GLFW bits. */
    public void mouse(WebPointerEvent event, int x, int y, int button, int modifiers, int clicks) {
        this.session.mouse(Objects.requireNonNull(event, "event"), x, y, button, modifiers, clicks);
    }

    /** Wheel deltas use Chromium's 120-unit wheel notches. Coordinates are CSS pixels. */
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
     * Sends an event with a JSON-encoded value to the connected page's {@code on(event, listener)}.
     * Returns false, without queuing, while no trusted document is connected.
     *
     * @throws IllegalArgumentException for an invalid name or a value above 1 MiB of JSON
     */
    public boolean emit(String event, Object data) {
        return this.session.emit(event, data);
    }

    /** Evaluates JavaScript and returns JSON-encoded by-value output ("null" for undefined). */
    public CompletableFuture<String> evaluate(String script) {
        return this.session.evaluate(script);
    }

    /** Owned browser-resolution CPU image, including the popup; fails until a frame exists. */
    public CompletableFuture<BufferedImage> screenshot() {
        return this.session.screenshot();
    }

    /** Releases GPU/cursor resources immediately and requests asynchronous native disposal. Idempotent. */
    @Override
    public void close() {
        this.session.close();
    }

    public static final class Builder extends WebBuilder<Builder> {

        private int width = 800;
        private int height = 600;
        private double scale = 1;

        private Builder(URI address) {
            super(address);
        }

        /** Page size in CSS pixels; defaults to 800x600. Each dimension is in 1..4096. */
        public Builder size(int width, int height) {
            SurfaceOptions.validateSize(width, height);
            this.width = width;
            this.height = height;
            return this;
        }

        /** Device pixel ratio; defaults to 1. The scaled size must stay within 4096 pixels. */
        public Builder scale(double scale) {
            SurfaceOptions.validateScale(1, 1, scale);
            this.scale = scale;
            return this;
        }

        /** Opens a new independent surface using a snapshot of these options. */
        public WebSurface open() {
            return new WebSurface(this.options(this.width, this.height, this.scale));
        }

        @Override
        protected Builder self() {
            return this;
        }
    }
}
