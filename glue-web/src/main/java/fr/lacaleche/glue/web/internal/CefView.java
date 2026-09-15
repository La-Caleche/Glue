package fr.lacaleche.glue.web.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.cef.CefBrowserSettings;
import org.cef.CefClient;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefBrowserWindowless;
import org.cef.browser.CefPaintEvent;
import org.cef.browser.CefRequestContext;
import org.cef.callback.CefDragData;
import org.cef.handler.CefRenderHandler;
import org.cef.handler.CefScreenInfo;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** CEF callbacks only capture owned bytes and metadata. They never access Minecraft or OpenGL. */
final class CefView extends CefBrowserWindowless implements CefRenderHandler {

    final FrameMailbox main = new FrameMailbox();
    final FrameMailbox popup = new FrameMailbox();
    final CompletableFuture<Void> ready = new CompletableFuture<>();
    final CompletableFuture<Void> disposed = new CompletableFuture<>();
    final ArrayBlockingQueue<String> messages = new ArrayBlockingQueue<>(128);
    private final AtomicReference<Boolean> requestedFocus = new AtomicReference<>();
    volatile boolean loading = true;
    volatile boolean canBack;
    volatile boolean canForward;
    volatile boolean popupVisible;
    volatile Rectangle popupBounds = new Rectangle();
    volatile String url;
    volatile String title = "";
    volatile String error = "";
    volatile int cursorType = Cursor.DEFAULT_CURSOR;
    private volatile Rectangle viewport;
    private final long nativeWindow;
    private final boolean transparent;
    private final WebOrigin messageOrigin;
    private final Component component = new Component() { };

    CefView(CefClient client, String url, boolean transparent, int width, int height, long nativeWindow, WebOrigin messageOrigin) {
        this(client, url, transparent, width, height, nativeWindow, messageOrigin, null, null);
    }

    private CefView(CefClient client, String url, boolean transparent, int width, int height, long nativeWindow,
                    WebOrigin messageOrigin, CefBrowserWindowless parent, Point inspectAt) {
        super(client, url, null, parent, inspectAt, settings());
        this.url = url;
        this.transparent = transparent;
        this.viewport = new Rectangle(0, 0, width, height);
        this.nativeWindow = nativeWindow;
        this.messageOrigin = messageOrigin;
    }

    @Override
    public void createImmediately() {
        this.setCloseAllowed();
        if (this.getParentBrowser() == null) {
            this.createBrowser(this.getClient(), this.nativeWindow, this.getUrl(), true, this.transparent, null, this.getRequestContext());
        } else {
            this.createDevTools(this.getParentBrowser(), this.getClient(), this.nativeWindow, true, false, null, this.getInspectAt());
        }
    }

    @Override
    protected CefBrowserWindowless createDevToolsBrowserWindowless(CefClient client, String url, CefRequestContext context, CefBrowserWindowless parent, Point inspectAt) {
        return new CefView(client, url, false, 1000, 700, this.nativeWindow, null, parent, inspectAt);
    }

    @Override
    public Component getUIComponent() {
        return this.component;
    }

    @Override
    public void setFocus(boolean focused) {
        // CefClient.onGotFocus echoes setFocus(true); avoid re-entering native focus for that echo.
        Boolean previous = this.requestedFocus.getAndSet(focused);
        if (previous == null || previous != focused) super.setFocus(focused);
    }

    @Override
    public CefRenderHandler getRenderHandler() {
        return this;
    }

    @Override
    public CompletableFuture<BufferedImage> createScreenshot(boolean nativeResolution) {
        try {
            BufferedImage image = this.main.screenshot();
            if (this.popupVisible && this.popup.capturedFrames() > 0) {
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.drawImage(this.popup.screenshot(), this.popupBounds.x, this.popupBounds.y, null);
                } finally {
                    graphics.dispose();
                }
            }
            return CompletableFuture.completedFuture(image);
        } catch (RuntimeException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public Rectangle getViewRect(CefBrowser browser) {
        return new Rectangle(this.viewport);
    }

    @Override
    public Point getScreenPoint(CefBrowser browser, Point viewPoint) {
        return new Point(viewPoint);
    }

    @Override
    public boolean getScreenInfo(CefBrowser browser, CefScreenInfo info) {
        info.Set(1, 32, 8, false, this.viewport, this.viewport);
        return true;
    }

    @Override
    public void onPopupShow(CefBrowser browser, boolean show) {
        this.popupVisible = show;
    }

    @Override
    public void onPopupSize(CefBrowser browser, Rectangle size) {
        this.popupBounds = new Rectangle(size);
    }

    @Override
    public void onPaint(CefBrowser browser, boolean isPopup, Rectangle[] rectangles, ByteBuffer buffer, int width, int height) {
        try {
            FrameMailbox mailbox = isPopup ? this.popup : this.main;
            mailbox.capture(rectangles, buffer, width, height);
        } catch (RuntimeException exception) {
            this.error = "Paint capture: " + exception.getMessage();
        }
    }

    @Override
    public void addOnPaintListener(Consumer<CefPaintEvent> listener) {
        throw new UnsupportedOperationException("Paint delivery is owned by Glue Web");
    }

    @Override
    public void setOnPaintListener(Consumer<CefPaintEvent> listener) {
        this.addOnPaintListener(listener);
    }

    @Override
    public void removeOnPaintListener(Consumer<CefPaintEvent> listener) {
        this.addOnPaintListener(listener);
    }

    @Override
    public boolean onCursorChange(CefBrowser browser, int cursorType) {
        this.cursorType = cursorType;
        return true;
    }

    @Override
    public boolean startDragging(CefBrowser browser, CefDragData dragData, int mask, int x, int y) {
        // Pointer drags (sliders, selection) remain native; OS file/HTML drag-and-drop is not hosted.
        return false;
    }

    @Override
    public void updateDragCursor(CefBrowser browser, int operation) {
        // There is no OS drag session when startDragging returns false.
    }

    @Override
    public synchronized void onBeforeClose() {
        super.onBeforeClose();
        this.main.close();
        this.popup.close();
        this.messages.clear();
        this.disposed.complete(null);
    }

    void resize(int width, int height) {
        if (this.viewport.width == width && this.viewport.height == height) return;
        this.viewport = new Rectangle(0, 0, width, height);
        if (this.ready.isDone()) this.wasResized(width, height);
    }

    boolean acceptsMessages(String address) {
        return this.messageOrigin != null && this.messageOrigin.matches(address);
    }

    CompletableFuture<JsonObject> evaluate(String expression) {
        return this.ready.thenCompose(ignored -> {
            JsonObject request = new JsonObject();
            request.addProperty("expression", expression);
            request.addProperty("returnByValue", true);
            request.addProperty("awaitPromise", true);
            return this.getDevToolsClient().executeDevToolsMethod("Runtime.evaluate", request.toString())
                    .thenApply(reply -> JsonParser.parseString(reply).getAsJsonObject());
        });
    }

    private static CefBrowserSettings settings() {
        CefBrowserSettings settings = new CefBrowserSettings();
        settings.windowless_frame_rate = 60;
        return settings;
    }

}
