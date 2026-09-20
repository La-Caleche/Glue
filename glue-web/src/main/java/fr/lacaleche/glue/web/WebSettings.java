package fr.lacaleche.glue.web;

import fr.lacaleche.glue.web.internal.options.SettingsFile;
import net.minecraft.client.Minecraft;

/**
 * Client settings shared by every page, persisted in {@code config/glue-web.json} and read on the
 * client thread.
 *
 * <p>The web scale is the number of screen pixels per CSS pixel, the unit of Minecraft's GUI scale.
 * Pages follow the GUI scale by default, which keeps them consistent with vanilla interfaces; a
 * fixed scale makes their density independent of it, which dense pages such as editors want. Open
 * pages apply a change on their next frame. Players edit these settings in Glue's own options page,
 * reached from Minecraft's options screen.</p>
 */
public final class WebSettings {

    public static final double MIN_SCALE = 1;
    public static final double MAX_SCALE = 4;
    /** The granularity the options page offers; any value inside the range is accepted. */
    public static final double SCALE_STEP = 0.25;

    private static SettingsFile.Values values;

    private WebSettings() {
    }

    /** Whether pages follow Minecraft's GUI scale; true until a fixed scale is chosen. */
    public static boolean followsGameScale() {
        return values().followsGameScale();
    }

    /** The fixed web scale, remembered even while pages follow the GUI scale. */
    public static double scale() {
        return values().scale();
    }

    /** The scale pages are sized with right now. */
    public static double effectiveScale() {
        return followsGameScale() ? Minecraft.getInstance().getWindow().getGuiScale() : scale();
    }

    public static void followGameScale() {
        store(new SettingsFile.Values(true, scale()));
    }

    /**
     * @throws IllegalArgumentException when the scale is not finite or leaves
     *                                  {@link #MIN_SCALE}..{@link #MAX_SCALE}
     */
    public static void setScale(double scale) {
        if (!Double.isFinite(scale) || scale < MIN_SCALE || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Web scale must be in " + MIN_SCALE + ".." + MAX_SCALE + ": " + scale);
        }
        store(new SettingsFile.Values(false, scale));
    }

    private static SettingsFile.Values values() {
        if (values == null) values = SettingsFile.load();
        return values;
    }

    private static void store(SettingsFile.Values updated) {
        if (updated.equals(values())) return;
        values = updated;
        SettingsFile.save(updated);
    }
}
