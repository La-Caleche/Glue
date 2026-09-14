package fr.lacaleche.jcef;

import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Cursor;
import java.util.HashMap;
import java.util.Map;

/** Client-thread cursor handles owned by one screen, not by the CEF callback thread. */
final class CefCursor implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("jcef-experiment");

    private final long window;
    private final Map<Integer, Long> handles = new HashMap<>();
    private int appliedShape;
    private boolean closed;

    CefCursor(long window) {
        this.window = window;
    }

    void apply(int cefType) {
        if (this.closed) return;
        int shape = shapeFor(cefType);
        if (shape == this.appliedShape) return;
        long handle = shape == GLFW.GLFW_ARROW_CURSOR ? 0 : this.handles.computeIfAbsent(shape, CefCursor::create);
        int actualShape = handle == 0 ? GLFW.GLFW_ARROW_CURSOR : shape;
        if (actualShape == this.appliedShape) return;
        GLFW.glfwSetCursor(this.window, handle);
        this.appliedShape = actualShape;
    }

    void reset() {
        this.apply(Cursor.DEFAULT_CURSOR);
    }

    int appliedShape() {
        return this.appliedShape == 0 ? GLFW.GLFW_ARROW_CURSOR : this.appliedShape;
    }

    @Override
    public void close() {
        if (this.closed) return;
        this.reset();
        this.closed = true;
        for (long handle : this.handles.values()) {
            if (handle != 0) GLFW.glfwDestroyCursor(handle);
        }
        this.handles.clear();
    }

    static int shapeFor(int cefType) {
        // The pinned JCEF display handler converts CEF cursor enums to java.awt.Cursor IDs.
        return switch (cefType) {
            case Cursor.HAND_CURSOR -> GLFW.GLFW_POINTING_HAND_CURSOR;
            case Cursor.TEXT_CURSOR -> GLFW.GLFW_IBEAM_CURSOR;
            case Cursor.CROSSHAIR_CURSOR -> GLFW.GLFW_CROSSHAIR_CURSOR;
            case Cursor.E_RESIZE_CURSOR, Cursor.W_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_EW_CURSOR;
            case Cursor.N_RESIZE_CURSOR, Cursor.S_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NS_CURSOR;
            case Cursor.NE_RESIZE_CURSOR, Cursor.SW_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NESW_CURSOR;
            case Cursor.NW_RESIZE_CURSOR, Cursor.SE_RESIZE_CURSOR -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
            case Cursor.MOVE_CURSOR -> GLFW.GLFW_RESIZE_ALL_CURSOR;
            default -> GLFW.GLFW_ARROW_CURSOR;
        };
    }

    private static long create(int shape) {
        long handle = GLFW.glfwCreateStandardCursor(shape);
        if (handle == 0) LOGGER.warn("GLFW cursor shape {} is unavailable; using the default cursor", shape);
        return handle;
    }
}
