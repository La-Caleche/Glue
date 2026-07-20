package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.controller.UIController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import fr.lacaleche.glue.mcsx.core.theme.Theme;
import fr.lacaleche.glue.mcsx.core.theme.Themes;
import fr.lacaleche.glue.mcsx.core.theme.Tokens;

import java.util.Map;

/** Controller for the live theme and utility stress screen. */
@UIController("mcsx:theme_stress")
public final class ThemeStressController extends ScreenController {

    private static final Theme AURORA = Themes.OBSIDIAN.withOverrides("aurora", Map.ofEntries(
            Map.entry(Tokens.SURFACE_BASE, 0xFF100D1C),
            Map.entry(Tokens.SURFACE_1, 0xE0181428),
            Map.entry(Tokens.SURFACE_2, 0xF0211B35),
            Map.entry(Tokens.SURFACE_3, 0x1AFFFFFF),
            Map.entry(Tokens.ACCENT, 0xFF8B5CF6),
            Map.entry(Tokens.ACCENT_HOVER, 0xFFA78BFA),
            Map.entry(Tokens.ACCENT_ACTIVE, 0xFF7C3AED),
            Map.entry(Tokens.ACCENT_CONTRAST, 0xFFFFFFFF),
            Map.entry(Tokens.ACCENT_SUBTLE, 0x338B5CF6),
            Map.entry(Tokens.RING, 0x998B5CF6)));

    private final Signal<String> input = signal("Live values survive theme changes");
    private final Signal<Boolean> enabled = signal(true);
    /** Kept open across a theme cycle: the modal scrim is a token too, and must repaint with the rest. */
    private final Signal<Boolean> scrimOpen = signal(false);
    private final Computed<String> themeName = computed(() -> Themes.active().name());

    private void openScrim() {
        scrimOpen.set(true);
    }

    private void closeScrim() {
        scrimOpen.set(false);
    }

    private void cycleTheme() {
        Theme current = Themes.active();
        if (current == Themes.OBSIDIAN) {
            Themes.active(Themes.FROST);
        } else if (current == Themes.FROST) {
            Themes.active(AURORA);
        } else {
            Themes.active(Themes.OBSIDIAN);
        }
    }
}
