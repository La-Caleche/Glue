package fr.lacaleche.glue.web.internal.host;

import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.browser.SurfaceOptions;
import net.minecraft.client.Minecraft;

/** Keeps host pages at one CSS pixel per GUI pixel within the browser's resolution limit. */
public final class HostSizing {

    private HostSizing() {
    }

    public static double scaleFor(int width, int height) {
        return SurfaceOptions.fitScale(width, height, Minecraft.getInstance().getWindow().getGuiScale());
    }

    public static void fit(WebSurface surface, int width, int height) {
        double scale = scaleFor(width, height);
        if (width != surface.width() || height != surface.height() || scale != surface.scale()) {
            surface.resize(width, height, scale);
        }
    }
}
