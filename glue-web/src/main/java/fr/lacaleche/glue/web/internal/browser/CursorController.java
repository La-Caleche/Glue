package fr.lacaleche.glue.web.internal.browser;

import fr.lacaleche.glue.web.WebCursor;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Cursor;
import java.util.EnumMap;
import java.util.Map;

/** Client-thread cursor ownership prevents closing an inactive surface from resetting another one. */
final class CursorController implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static CursorController owner;

    private final long window;
    private final Map<WebCursor, Long> handles = new EnumMap<>(WebCursor.class);
    private WebCursor applied = WebCursor.ARROW;

    CursorController(long window) {
        this.window = window;
    }

    void apply(WebCursor shape) {
        if (owner == this && shape == this.applied) return;
        long handle = shape == WebCursor.ARROW ? 0 : this.handles.computeIfAbsent(shape, CursorController::create);
        GLFW.glfwSetCursor(this.window, handle);
        this.applied = handle == 0 ? WebCursor.ARROW : shape;
        owner = this;
    }

    void reset() {
        if (owner != this) return;
        GLFW.glfwSetCursor(this.window, 0);
        this.applied = WebCursor.ARROW;
        owner = null;
    }

    WebCursor applied() {
        return owner == this ? this.applied : WebCursor.ARROW;
    }

    @Override
    public void close() {
        this.reset();
        for (long handle : this.handles.values()) {
            if (handle != 0) GLFW.glfwDestroyCursor(handle);
        }
        this.handles.clear();
    }

    static WebCursor fromNative(int type) {
        // JCEF's native display handler sends java.awt.Cursor identifiers, not CEF enum ordinals.
        return switch (type) {
            case Cursor.HAND_CURSOR -> WebCursor.HAND;
            case Cursor.TEXT_CURSOR -> WebCursor.TEXT;
            case Cursor.CROSSHAIR_CURSOR -> WebCursor.CROSSHAIR;
            case Cursor.E_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR -> WebCursor.RESIZE_EW;
            case Cursor.N_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR -> WebCursor.RESIZE_NS;
            case Cursor.NE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR -> WebCursor.RESIZE_NESW;
            case Cursor.NW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR -> WebCursor.RESIZE_NWSE;
            case Cursor.MOVE_CURSOR -> WebCursor.MOVE;
            default -> WebCursor.ARROW;
        };
    }

    private static long create(WebCursor cursor) {
        int shape = switch (cursor) {
            case HAND -> GLFW.GLFW_POINTING_HAND_CURSOR;
            case TEXT -> GLFW.GLFW_IBEAM_CURSOR;
            case CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case RESIZE_EW -> GLFW.GLFW_RESIZE_EW_CURSOR;
            case RESIZE_NS -> GLFW.GLFW_RESIZE_NS_CURSOR;
            case RESIZE_NESW -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            case RESIZE_NWSE -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case MOVE -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            case ARROW -> GLFW.GLFW_ARROW_CURSOR;
        };
        long handle = GLFW.glfwCreateStandardCursor(shape);
        if (handle == 0) LOGGER.warn("Cursor {} is unavailable; using the arrow", cursor);
        return handle;
    }
}
