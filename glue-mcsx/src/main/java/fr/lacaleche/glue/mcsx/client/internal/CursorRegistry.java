package fr.lacaleche.glue.mcsx.client.internal;

import fr.lacaleche.glue.mcsx.Mcsx;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

public final class CursorRegistry {

    public static final int CROSSHAIR = 10_000;
    public static final int MOVE = 10_001;
    public static final int FORBIDDEN = 10_002;
    public static final int RESIZE_HORIZONTAL = 10_003;
    public static final int RESIZE_VERTICAL = 10_004;
    public static final int RESIZE_NORTH_WEST_SOUTH_EAST = 10_005;
    public static final int RESIZE_NORTH_EAST_SOUTH_WEST = 10_006;

    /** GLFW shape per custom type, indexed by {@code type - CROSSHAIR}. */
    private static final int[] SHAPES = {
            GLFW.GLFW_CROSSHAIR_CURSOR,
            GLFW.GLFW_RESIZE_ALL_CURSOR,
            GLFW.GLFW_NOT_ALLOWED_CURSOR,
            GLFW.GLFW_RESIZE_EW_CURSOR,
            GLFW.GLFW_RESIZE_NS_CURSOR,
            GLFW.GLFW_RESIZE_NWSE_CURSOR,
            GLFW.GLFW_RESIZE_NESW_CURSOR,
    };

    private static final Constructor<PointerIcon> ICON_CONSTRUCTOR = findIconConstructor();
    private static final PointerIcon[] ICONS = createIcons(MemoryUtil.NULL);
    private static volatile PointerIcon[] nativeIcons = new PointerIcon[SHAPES.length];
    private static long[] ownedHandles = new long[0];
    private static final Map<View, PointerIcon> viewCursors = new WeakHashMap<>();

    private CursorRegistry() {
    }

    public static void initialize() {
        if (!Minecraft.getInstance().isSameThread()) {
            throw new IllegalStateException("Cursor registry must initialize on the Minecraft thread");
        }
        if (ownedHandles.length != 0 || ICON_CONSTRUCTOR == null) return;

        long[] handles = new long[SHAPES.length];
        PointerIcon[] icons = new PointerIcon[SHAPES.length];
        for (int index = 0; index < SHAPES.length; index++) {
            long handle = GLFW.glfwCreateStandardCursor(SHAPES[index]);
            if (handle == MemoryUtil.NULL) {
                Mcsx.LOGGER.warn("GLFW cursor shape {} is unavailable", SHAPES[index]);
                continue;
            }
            icons[index] = createIcon(CROSSHAIR + index, handle);
            if (icons[index] == null) GLFW.glfwDestroyCursor(handle);
            else handles[index] = handle;
        }
        ownedHandles = handles;
        nativeIcons = icons;
    }

    public static void close(Minecraft minecraft) {
        if (!minecraft.isSameThread()) {
            throw new IllegalStateException("Cursor registry must close on the Minecraft thread");
        }

        GLFW.glfwSetCursor(minecraft.getWindow().getWindow(), MemoryUtil.NULL);
        for (long handle : ownedHandles) {
            if (handle != MemoryUtil.NULL) GLFW.glfwDestroyCursor(handle);
        }
        ownedHandles = new long[0];
        nativeIcons = new PointerIcon[SHAPES.length];
    }

    public static PointerIcon icon(int type) {
        int index = type - CROSSHAIR;
        PointerIcon icon = index >= 0 && index < ICONS.length ? ICONS[index] : null;
        return icon != null ? icon : PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
    }

    public static PointerIcon resolve(int type) {
        int index = type - CROSSHAIR;
        PointerIcon custom = index >= 0 && index < SHAPES.length ? nativeIcons[index] : null;
        return custom != null ? custom : PointerIcon.getSystemIcon(type);
    }

    public static void set(View view, PointerIcon cursor) {
        viewCursors.put(Objects.requireNonNull(view, "view"), Objects.requireNonNull(cursor, "cursor"));
    }

    public static void clear(View view) {
        viewCursors.remove(Objects.requireNonNull(view, "view"));
    }

    public static PointerIcon cursor(View view) {
        return viewCursors.get(view);
    }

    private static PointerIcon[] createIcons(long handle) {
        PointerIcon[] icons = new PointerIcon[SHAPES.length];
        if (ICON_CONSTRUCTOR == null) return icons;

        for (int index = 0; index < SHAPES.length; index++) {
            icons[index] = createIcon(CROSSHAIR + index, handle);
        }
        return icons;
    }

    private static PointerIcon createIcon(int type, long handle) {
        try {
            return ICON_CONSTRUCTOR.newInstance(type, handle);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException exception) {
            Mcsx.LOGGER.warn("Could not create ModernUI pointer icon type {}", type, exception);
            return null;
        }
    }

    private static Constructor<PointerIcon> findIconConstructor() {
        try {
            Constructor<PointerIcon> constructor = PointerIcon.class.getDeclaredConstructor(int.class, long.class);
            constructor.setAccessible(true);
            return constructor;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Mcsx.LOGGER.warn("Custom MCSX cursors are unavailable", exception);
            return null;
        }
    }
}
