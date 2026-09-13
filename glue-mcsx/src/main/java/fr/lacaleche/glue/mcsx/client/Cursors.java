package fr.lacaleche.glue.mcsx.client;

import fr.lacaleche.glue.mcsx.client.internal.CursorRegistry;
import icyllis.modernui.view.PointerIcon;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/** Standard pointer icons for ModernUI Views hosted by MCSX. */
@Environment(EnvType.CLIENT)
public final class Cursors {

    private Cursors() {
    }

    public static PointerIcon pointer() {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_DEFAULT);
    }

    public static PointerIcon hand() {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_HAND);
    }

    public static PointerIcon text() {
        return PointerIcon.getSystemIcon(PointerIcon.TYPE_TEXT);
    }

    public static PointerIcon crosshair() {
        return CursorRegistry.icon(CursorRegistry.CROSSHAIR);
    }

    public static PointerIcon move() {
        return CursorRegistry.icon(CursorRegistry.MOVE);
    }

    public static PointerIcon forbidden() {
        return CursorRegistry.icon(CursorRegistry.FORBIDDEN);
    }

    public static PointerIcon resizeHorizontal() {
        return CursorRegistry.icon(CursorRegistry.RESIZE_HORIZONTAL);
    }

    public static PointerIcon resizeVertical() {
        return CursorRegistry.icon(CursorRegistry.RESIZE_VERTICAL);
    }

    public static PointerIcon resizeNorthWestSouthEast() {
        return CursorRegistry.icon(CursorRegistry.RESIZE_NORTH_WEST_SOUTH_EAST);
    }

    public static PointerIcon resizeNorthEastSouthWest() {
        return CursorRegistry.icon(CursorRegistry.RESIZE_NORTH_EAST_SOUTH_WEST);
    }

    public static <V extends View> V set(V view, PointerIcon cursor) {
        CursorRegistry.set(view, cursor);
        return view;
    }

    public static <V extends View> V clear(V view) {
        CursorRegistry.clear(view);
        return view;
    }
}
