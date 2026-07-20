package fr.lacaleche.glue.mcsx.cursor;

import icyllis.modernui.view.PointerIcon;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Custom mouse cursors for MCSX. Minecraft 1.21.8 has no cursor API and Modern
 * UI only exposes ARROW/HAND/TEXT, but its {@link PointerIcon} simply wraps a GLFW
 * standard-cursor handle. We create the resize/move GLFW cursors (GLFW 3.4, bundled
 * with the game's LWJGL) and wrap them through the private constructor.
 *
 * <p>Modern UI's view-root resolves a cursor to its <em>type int</em> and hands only
 * that to the host's {@code applyPointerIcon}, which normally re-derives the handle
 * via {@link PointerIcon#getSystemIcon} — discarding any custom handle. So we also
 * keep a {@code type → handle} registry; the MCSX Modern UI host consults
 * {@link #handleFor} so custom cursors survive that round-trip.
 *
 * <p>Cursors are created lazily on the UI thread (where Modern UI also creates its
 * own) and cached; if reflection or a GLFW cursor is unavailable the default cursor
 * is returned, so callers degrade gracefully.
 */
public final class Cursors {

    private Cursors() {
    }

    private static final int BASE_TYPE = 9000;
    private static final Map<Integer, Long> HANDLES = new ConcurrentHashMap<>();

    /** One icon per GLFW shape, created on first request. Keyed by shape, so the cache is the state. */
    private static final Map<Integer, PointerIcon> ICONS = new ConcurrentHashMap<>();

    /**
     * The private {@code PointerIcon(type, handle)} constructor, or null if it could not be reached.
     * Held in a nested class so the lookup runs once, on first use, without a mutable "already
     * tried" flag — class initialization gives that for free.
     */
    private static final class Reflected {

        static final Constructor<PointerIcon> CONSTRUCTOR = lookUp();

        private static Constructor<PointerIcon> lookUp() {
            try {
                Constructor<PointerIcon> found =
                        PointerIcon.class.getDeclaredConstructor(int.class, long.class);
                found.setAccessible(true);
                return found;
            } catch (Throwable e) {
                return null;
            }
        }
    }

    public static PointerIcon hand() {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    public static PointerIcon resizeEW() {
        return of(GLFW.GLFW_RESIZE_EW_CURSOR);
    }

    public static PointerIcon resizeNS() {
        return of(GLFW.GLFW_RESIZE_NS_CURSOR);
    }

    public static PointerIcon resizeNWSE() {
        return of(GLFW.GLFW_RESIZE_NWSE_CURSOR);
    }

    public static PointerIcon resizeNESW() {
        return of(GLFW.GLFW_RESIZE_NESW_CURSOR);
    }

    public static PointerIcon move() {
        return of(GLFW.GLFW_RESIZE_ALL_CURSOR);
    }

    /**
     * Host hook: the GLFW cursor handle for a resolved pointer {@code type}, or
     * {@code fallback} when {@code type} is not one of our custom cursors.
     */
    public static long handleFor(int type, long fallback) {
        Long handle = HANDLES.get(type);
        return handle != null ? handle : fallback;
    }

    /** {@code computeIfAbsent} so a shape can never create two GLFW cursors and leak one. */
    private static PointerIcon of(int glfwShape) {
        return ICONS.computeIfAbsent(glfwShape, Cursors::make);
    }

    private static PointerIcon make(int glfwShape) {
        PointerIcon fallback = PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
        if (Reflected.CONSTRUCTOR == null) {
            return fallback;
        }
        try {
            long handle = GLFW.glfwCreateStandardCursor(glfwShape);
            if (handle == 0L) {
                return fallback;
            }
            int type = BASE_TYPE + glfwShape;
            PointerIcon icon = Reflected.CONSTRUCTOR.newInstance(type, handle);
            HANDLES.put(type, handle);
            return icon;
        } catch (Throwable e) {
            return fallback;
        }
    }
}
