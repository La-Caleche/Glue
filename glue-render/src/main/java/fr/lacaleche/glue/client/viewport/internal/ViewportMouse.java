package fr.lacaleche.glue.client.viewport.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Pure conversion from a raw window cursor coordinate to a viewport-local GUI coordinate.
 *
 * <p>GLFW reports the cursor in screen coordinates, which differ from framebuffer pixels on high-DPI
 * displays; a GUI pass confined to a viewport lays out in GUI units translated to the viewport's
 * framebuffer origin. The mapping therefore goes screen&nbsp;→ framebuffer&nbsp;→ viewport-local
 * framebuffer&nbsp;→ GUI units, per axis. It deliberately reads none of the window's cached GUI
 * dimensions, which the viewport render scopes temporarily narrow.</p>
 */
@Environment(EnvType.CLIENT)
public final class ViewportMouse {

    private ViewportMouse() {
    }

    /**
     * @param raw            the cursor position on this axis, in screen coordinates
     * @param screenExtent   the window extent on this axis, in the same screen coordinates
     * @param frameExtent    the full-window framebuffer extent on this axis
     * @param viewportOrigin the viewport origin on this axis, in framebuffer pixels
     * @param guiScale       the active GUI scale
     */
    public static double toViewportGui(double raw, int screenExtent, int frameExtent,
                                       int viewportOrigin, int guiScale) {
        return (raw * frameExtent / screenExtent - viewportOrigin) / guiScale;
    }

    /** Maps a raw screen-coordinate movement delta into viewport GUI units. */
    public static double deltaToViewportGui(double delta, int screenExtent, int frameExtent, int guiScale) {
        return delta * frameExtent / screenExtent / guiScale;
    }
}
