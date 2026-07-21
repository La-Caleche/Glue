package fr.lacaleche.glue.mcsx.core.theme;

import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.util.Map;
import java.util.Objects;

/**
 * The two built-in themes and the mutable active selection. The obsidian/frost values are the
 * ground-truth palette (ARCHITECTURE §10.2). {@code active} is unsynchronized — read/written on the
 * render/UI thread only.
 */
public final class Themes {

    public static final Theme OBSIDIAN = new Theme("obsidian", Map.ofEntries(
            Map.entry(Tokens.TEXT_PRIMARY, 0xFFEDEDED),
            Map.entry(Tokens.TEXT_MUTED, 0xFF9AA6B2),
            Map.entry(Tokens.TEXT_SUBTLE, 0xFF6B7682),
            Map.entry(Tokens.SURFACE_BASE, 0xFF0B0E12),
            Map.entry(Tokens.SURFACE_1, 0xD114191F),
            Map.entry(Tokens.SURFACE_2, 0xF01E242C),
            Map.entry(Tokens.SURFACE_3, 0x0DFFFFFF),
            Map.entry(Tokens.SURFACE_HOVER, 0x0FFFFFFF),
            Map.entry(Tokens.SURFACE_ACTIVE, 0x1AFFFFFF),
            Map.entry(Tokens.SCRIM, 0x8C06080A),
            Map.entry(Tokens.BORDER, 0x17FFFFFF),
            Map.entry(Tokens.BORDER_STRONG, 0x29FFFFFF),
            Map.entry(Tokens.ACCENT, 0xFF10B981),
            Map.entry(Tokens.ACCENT_HOVER, 0xFF34D399),
            Map.entry(Tokens.ACCENT_ACTIVE, 0xFF059669),
            Map.entry(Tokens.ACCENT_CONTRAST, 0xFF04140D),
            Map.entry(Tokens.ACCENT_SUBTLE, 0x2410B981),
            Map.entry(Tokens.RING, 0x8C10B981),
            Map.entry(Tokens.STATUS_SUCCESS, 0xFF34D399),
            Map.entry(Tokens.STATUS_WARNING, 0xFFFBBF24),
            Map.entry(Tokens.STATUS_DANGER, 0xFFF87171),
            Map.entry(Tokens.STATUS_INFO, 0xFF38BDF8),
            Map.entry(Tokens.STATUS_DANGER_CONTRAST, 0xFF2A0A0A),
            Map.entry(Tokens.STATUS_SUCCESS_SUBTLE, 0x2434D399),
            Map.entry(Tokens.STATUS_WARNING_SUBTLE, 0x24FBBF24),
            Map.entry(Tokens.STATUS_DANGER_SUBTLE, 0x24F87171),
            Map.entry(Tokens.STATUS_INFO_SUBTLE, 0x2438BDF8)));

    public static final Theme FROST = new Theme("frost", Map.ofEntries(
            Map.entry(Tokens.TEXT_PRIMARY, 0xFF0B1220),
            Map.entry(Tokens.TEXT_MUTED, 0xFF51606E),
            Map.entry(Tokens.TEXT_SUBTLE, 0xFF8492A0),
            Map.entry(Tokens.SURFACE_BASE, 0xFFDFE5EC),
            Map.entry(Tokens.SURFACE_1, 0xD9FFFFFF),
            Map.entry(Tokens.SURFACE_2, 0xF2FFFFFF),
            Map.entry(Tokens.SURFACE_3, 0x0A0F172A),
            Map.entry(Tokens.SURFACE_HOVER, 0x0D0F172A),
            Map.entry(Tokens.SURFACE_ACTIVE, 0x170F172A),
            Map.entry(Tokens.SCRIM, 0x470F172A),
            Map.entry(Tokens.BORDER, 0x1A0F172A),
            Map.entry(Tokens.BORDER_STRONG, 0x2B0F172A),
            Map.entry(Tokens.ACCENT, 0xFF059669),
            Map.entry(Tokens.ACCENT_HOVER, 0xFF047857),
            Map.entry(Tokens.ACCENT_ACTIVE, 0xFF065F46),
            Map.entry(Tokens.ACCENT_CONTRAST, 0xFFFFFFFF),
            Map.entry(Tokens.ACCENT_SUBTLE, 0x1F059669),
            Map.entry(Tokens.RING, 0x73059669),
            Map.entry(Tokens.STATUS_SUCCESS, 0xFF059669),
            Map.entry(Tokens.STATUS_WARNING, 0xFFD97706),
            Map.entry(Tokens.STATUS_DANGER, 0xFFDC2626),
            Map.entry(Tokens.STATUS_INFO, 0xFF0284C7),
            Map.entry(Tokens.STATUS_DANGER_CONTRAST, 0xFFFFFFFF),
            Map.entry(Tokens.STATUS_SUCCESS_SUBTLE, 0x1F059669),
            Map.entry(Tokens.STATUS_WARNING_SUBTLE, 0x1FD97706),
            Map.entry(Tokens.STATUS_DANGER_SUBTLE, 0x1FDC2626),
            Map.entry(Tokens.STATUS_INFO_SUBTLE, 0x1F0284C7)));

    private static final Signal<Theme> ACTIVE = new Signal<>(OBSIDIAN);

    private Themes() {
    }

    public static Theme active() {
        return ACTIVE.get();
    }

    public static void active(Theme theme) {
        ACTIVE.set(Objects.requireNonNull(theme, "theme"));
    }

    public static void toggle() {
        active(active() == OBSIDIAN ? FROST : OBSIDIAN);
    }
}
