package fr.lacaleche.glue.mcsx.core.theme;

/**
 * The canonical design-token keys. A {@code Theme} maps each of these to a packed ARGB color;
 * the Tailwind color utilities ({@code bg-surface}, {@code text-muted}, {@code border-strong}, …)
 * resolve to one of these keys, and the value is looked up against the active theme at apply
 * time. This is the single source of truth for token names — pure {@code java.*}, no colors.
 */
public final class Tokens {

    private Tokens() {
    }

    public static final String TEXT_PRIMARY = "text.primary";
    public static final String TEXT_MUTED = "text.muted";
    public static final String TEXT_SUBTLE = "text.subtle";

    public static final String SURFACE_BASE = "surface.base";
    public static final String SURFACE_1 = "surface.1";
    public static final String SURFACE_2 = "surface.2";
    public static final String SURFACE_3 = "surface.3";
    public static final String SURFACE_HOVER = "surface.hover";
    public static final String SURFACE_ACTIVE = "surface.active";

    public static final String SCRIM = "scrim";
    public static final String BORDER = "border";
    public static final String BORDER_STRONG = "border.strong";

    public static final String ACCENT = "accent";
    public static final String ACCENT_HOVER = "accent.hover";
    public static final String ACCENT_ACTIVE = "accent.active";
    public static final String ACCENT_CONTRAST = "accent.contrast";
    public static final String ACCENT_SUBTLE = "accent.subtle";
    public static final String RING = "ring";

    public static final String STATUS_SUCCESS = "status.success";
    public static final String STATUS_WARNING = "status.warning";
    public static final String STATUS_DANGER = "status.danger";
    public static final String STATUS_INFO = "status.info";

    /** Text/icon color on a filled {@code danger} surface — light red wants dark ink, dark red wants white. */
    public static final String STATUS_DANGER_CONTRAST = "status.danger.contrast";

    /** Low-alpha tints of the status hues — the fill behind an alert or a status badge. */
    public static final String STATUS_SUCCESS_SUBTLE = "status.success.subtle";
    public static final String STATUS_WARNING_SUBTLE = "status.warning.subtle";
    public static final String STATUS_DANGER_SUBTLE = "status.danger.subtle";
    public static final String STATUS_INFO_SUBTLE = "status.info.subtle";
}
