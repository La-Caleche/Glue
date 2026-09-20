package fr.lacaleche.glue.web.internal.host;

import fr.lacaleche.glue.web.WebSettings;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.internal.browser.SurfaceOptions;
import net.minecraft.client.Minecraft;

/** Keeps host pages at one CSS pixel per web-scale pixel within the browser's resolution limit. */
public final class HostSizing {

    private HostSizing() {
    }

    /** The page size and device pixel ratio for a rectangle given in GUI pixels. */
    public static Fit fit(int guiWidth, int guiHeight) {
        return fit(guiWidth, guiHeight, Minecraft.getInstance().getWindow().getGuiScale(), WebSettings.effectiveScale());
    }

    /** Resizes the page when its rectangle, the GUI scale or the web scale changed. */
    public static void apply(WebSurface surface, int guiWidth, int guiHeight) {
        Fit fit = fit(guiWidth, guiHeight);
        if (fit.width() != surface.width() || fit.height() != surface.height() || fit.scale() != surface.scale()) {
            surface.resize(fit.width(), fit.height(), fit.scale());
        }
    }

    /**
     * A page always covers its rectangle on screen; the web scale decides how many CSS pixels it is
     * given to do it with. A scale below the GUI scale buys CSS pixels and a denser page, a scale
     * above it enlarges the page. The density is reduced when the page would otherwise exceed the
     * browser's maximum dimension.
     */
    static Fit fit(int guiWidth, int guiHeight, double gameScale, double webScale) {
        double density = Math.min(gameScale / webScale,
                (double) WebSurface.MAX_DIMENSION / Math.max(1, Math.max(guiWidth, guiHeight)));
        int width = Math.max(1, (int) Math.round(guiWidth * density));
        int height = Math.max(1, (int) Math.round(guiHeight * density));
        return new Fit(width, height, SurfaceOptions.fitScale(width, height, gameScale / density));
    }

    /** A page size in CSS pixels with the device pixel ratio that maps it to browser pixels. */
    public record Fit(int width, int height, double scale) {
    }
}
