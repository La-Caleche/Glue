package fr.lacaleche.glue.mcsx.client.theme;

import icyllis.modernui.R;
import icyllis.modernui.graphics.Color;
import icyllis.modernui.util.ColorStateList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public final class ThemeTokens {

    public static final Token<Integer> SURFACE = Token.of(
            "surface",
            Color.rgb(25, 28, 32)
    );
    public static final Token<Integer> SURFACE_RAISED = Token.of(
            "surface-raised",
            Color.rgb(38, 43, 49)
    );
    public static final Token<Integer> SURFACE_WELL = Token.of("surface-well", Color.rgb(18, 21, 24));
    public static final Token<Integer> SURFACE_HEADER = Token.of("surface-header", Color.rgb(31, 35, 40));
    public static final Token<Integer> SURFACE_FOOTER = Token.of("surface-footer", Color.rgb(20, 23, 26));
    public static final Token<Integer> SURFACE_HOVER = Token.of("surface-hover", Color.rgb(47, 53, 60));
    public static final Token<Integer> SURFACE_QUIET_HOVER = Token.of(
            "surface-quiet-hover",
            Color.rgb(30, 34, 38)
    );
    public static final Token<Integer> SURFACE_PRESSED = Token.of("surface-pressed", Color.rgb(27, 31, 35));
    public static final Token<Integer> SURFACE_DISABLED = Token.of("surface-disabled", Color.rgb(30, 34, 38));
    public static final Token<Integer> TEXT_PRIMARY = Token.of("text-primary", Color.rgb(238, 241, 244));
    public static final Token<Integer> TEXT_MUTED = Token.of(
            "text-muted",
            Color.rgb(182, 190, 197)
    );
    public static final Token<Integer> TEXT_EXPLANATORY = Token.of("text-explanatory", Color.rgb(139, 147, 155));
    public static final Token<Integer> TEXT_SUBTITLE = Token.of("text-subtitle", Color.rgb(127, 136, 143));
    public static final Token<Integer> TEXT_META = Token.of("text-meta", Color.rgb(111, 119, 126));
    public static final Token<Integer> TEXT_LABEL = Token.of("text-label", Color.rgb(92, 101, 108));
    public static final Token<Integer> TEXT_DISABLED = Token.of("text-disabled", Color.rgb(77, 85, 92));
    public static final Token<Integer> TEXT_ON_ACCENT = Token.of("text-on-accent", Color.rgb(244, 247, 249));
    public static final Token<Integer> TEXT_ON_LIGHT = Token.of("text-on-light", Color.rgb(25, 28, 32));
    public static final Token<Integer> ACCENT = Token.of(
            "accent",
            Color.rgb(61, 70, 79)
    );
    public static final Token<Integer> ACCENT_HOVER = Token.of("accent-hover", Color.rgb(73, 83, 93));
    public static final Token<Integer> CONTROL_ON = Token.of("control-on", Color.rgb(205, 212, 218));
    public static final Token<Integer> DANGER = Token.of(
            "danger",
            Color.rgb(36, 23, 21)
    );
    public static final Token<Integer> DANGER_HOVER = Token.of("danger-hover", Color.rgb(44, 28, 25));
    public static final Token<Integer> DANGER_FIELD = Token.of("danger-field", Color.rgb(22, 16, 15));
    public static final Token<Integer> DANGER_TEXT = Token.of("danger-text", Color.rgb(193, 144, 134));
    public static final Token<Integer> DANGER_TEXT_HOVER = Token.of("danger-text-hover", Color.rgb(226, 183, 173));
    public static final Token<Integer> DANGER_LINE = Token.of("danger-line", Color.rgb(171, 117, 104));
    public static final Token<Integer> HUD_SURFACE = Token.of(
            "hud-surface",
            withAlpha(SURFACE.fallback(), 224)
    );
    public static final Token<Integer> HUD_SURFACE_STRONG = Token.of(
            "hud-surface-strong",
            withAlpha(SURFACE_WELL.fallback(), 199)
    );
    public static final Token<ColorStateList> CHECKBOX_INDICATOR = Token.of(
            "checkbox-indicator",
            checkboxIndicator(
                    CONTROL_ON.fallback(),
                    TEXT_PRIMARY.fallback(),
                    TEXT_MUTED.fallback()
            )
    );
    public static final Token<Integer> CONTROL_HEIGHT = Token.of("control-height", 32);
    public static final Token<Integer> CORNER_RADIUS = Token.of("corner-radius", 3);
    public static final Token<Integer> CHIP_RADIUS = Token.of("chip-radius", 2);
    public static final Token<Integer> SHELL_RADIUS = Token.of("shell-radius", 4);
    public static final Token<Integer> DOCK_BACKGROUND = Token.of("dock-background", Color.rgb(11, 12, 13));
    public static final Token<Integer> DOCK_PANE_BACKGROUND = Token.of("dock-pane-background", SURFACE.fallback());
    public static final Token<Integer> DOCK_HEADER_BACKGROUND = Token.of(
            "dock-header-background",
            SURFACE_HEADER.fallback()
    );
    public static final Token<Integer> DOCK_BORDER = Token.of("dock-border", SURFACE_WELL.fallback());
    public static final Token<Integer> DOCK_ACTIVE_TAB_BACKGROUND = Token.of(
            "dock-active-tab-background",
            SURFACE_HOVER.fallback()
    );
    public static final Token<Integer> DOCK_DRAG_GHOST_BACKGROUND = Token.of(
            "dock-drag-ghost-background",
            withAlpha(SURFACE_RAISED.fallback(), 232)
    );
    public static final Token<Integer> DOCK_DROP_HIGHLIGHT = Token.of(
            "dock-drop-highlight",
            withAlpha(CONTROL_ON.fallback(), 92)
    );
    public static final Token<DockMetrics> DOCK_METRICS = Token.of(
            "dock-metrics",
            new DockMetrics(12, 12, 36, 4, 0, 11, 10, 14, 8, 32, 32)
    );

    private ThemeTokens() {
    }

    public static int withAlpha(int color, int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("Alpha must be 0..255: " + alpha);

        return (alpha << 24) | (color & 0x00ffffff);
    }

    static ColorStateList checkboxIndicator(int accent, int primary, int muted) {
        return new ColorStateList(
                new int[][]{
                        {-R.attr.state_enabled},
                        {R.attr.state_indeterminate, R.attr.state_pressed},
                        {R.attr.state_checked, R.attr.state_pressed},
                        {R.attr.state_pressed},
                        {R.attr.state_indeterminate, R.attr.state_focused},
                        {R.attr.state_checked, R.attr.state_focused},
                        {R.attr.state_focused},
                        {R.attr.state_indeterminate, R.attr.state_hovered},
                        {R.attr.state_checked, R.attr.state_hovered},
                        {R.attr.state_hovered},
                        {R.attr.state_indeterminate},
                        {R.attr.state_checked},
                        {}
                },
                new int[]{
                        ColorStateList.modulateColor(muted, 0.38f),
                        blend(accent, primary, 0.34f),
                        blend(accent, primary, 0.28f),
                        blend(muted, accent, 0.50f),
                        blend(accent, primary, 0.26f),
                        blend(accent, primary, 0.20f),
                        blend(muted, accent, 0.35f),
                        blend(accent, primary, 0.18f),
                        blend(accent, primary, 0.12f),
                        blend(muted, accent, 0.20f),
                        blend(accent, primary, 0.10f),
                        accent,
                        muted
                }
        );
    }

    private static int blend(int from, int to, float amount) {
        float retained = 1.0f - amount;
        int alpha = Math.round(
                ((from >>> 24) & 0xff) * retained + ((to >>> 24) & 0xff) * amount
        );
        int red = Math.round(
                ((from >>> 16) & 0xff) * retained + ((to >>> 16) & 0xff) * amount
        );
        int green = Math.round(
                ((from >>> 8) & 0xff) * retained + ((to >>> 8) & 0xff) * amount
        );
        int blue = Math.round((from & 0xff) * retained + (to & 0xff) * amount);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }
}
