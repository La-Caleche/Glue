package fr.lacaleche.glue.mcsx.core.theme;

import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThemeTest {

    @AfterEach
    void resetActive() {
        Themes.active(Themes.OBSIDIAN);
    }

    @Test
    void resolvesKnownTokensPerTheme() {
        assertEquals(0xFF10B981, Themes.OBSIDIAN.color(Tokens.ACCENT));
        assertEquals(0xFF059669, Themes.FROST.color(Tokens.ACCENT));
    }

    @Test
    void everyTokenIsDefinedInBothThemes() {
        List<String> keys = allTokenKeys();
        assertFalse(keys.isEmpty(), "reflection found no token constants");
        for (String key : keys) {
            assertNotEquals(Theme.MISSING, Themes.OBSIDIAN.color(key), "obsidian missing " + key);
            assertNotEquals(Theme.MISSING, Themes.FROST.color(key), "frost missing " + key);
        }
    }

    @Test
    void unknownTokenReturnsLoudMagenta() {
        assertEquals(Theme.MISSING, Themes.OBSIDIAN.color("does.not.exist"));
    }

    @Test
    void toggleSwapsActiveTheme() {
        assertEquals(Themes.OBSIDIAN, Themes.active());
        Themes.toggle();
        assertEquals(Themes.FROST, Themes.active());
        Themes.toggle();
        assertEquals(Themes.OBSIDIAN, Themes.active());
    }

    @Test
    void derivesACompleteThemeFromFocusedOverrides() {
        Theme aurora = Themes.OBSIDIAN.withOverrides("aurora", Map.of(
                Tokens.ACCENT, 0xFF8B5CF6,
                Tokens.SURFACE_BASE, 0xFF100D1C));

        assertEquals("aurora", aurora.name());
        assertEquals(0xFF8B5CF6, aurora.color(Tokens.ACCENT));
        assertEquals(0xFF100D1C, aurora.color(Tokens.SURFACE_BASE));
        assertEquals(Themes.OBSIDIAN.color(Tokens.TEXT_PRIMARY), aurora.color(Tokens.TEXT_PRIMARY));
        for (String key : allTokenKeys()) {
            assertNotEquals(Theme.MISSING, aurora.color(key), "derived theme missing " + key);
        }
    }

    @Test
    void themeCopiesItsInputAndExposesAnImmutablePalette() {
        Map<String, Integer> source = new HashMap<>(Themes.OBSIDIAN.colors());
        Theme theme = new Theme("copy", source);
        source.put(Tokens.ACCENT, 0);

        assertEquals(Themes.OBSIDIAN.color(Tokens.ACCENT), theme.color(Tokens.ACCENT));
        assertThrows(UnsupportedOperationException.class,
                () -> theme.colors().put(Tokens.ACCENT, 0));
        assertThrows(IllegalArgumentException.class, () -> new Theme(" ", Map.of()));
        assertThrows(NullPointerException.class, () -> Themes.active(null));
    }

    @Test
    void activeThemeParticipatesInTheReactiveGraph() {
        List<String> observed = new ArrayList<>();
        Effect effect = Effect.of(() -> observed.add(Themes.active().name()));

        Themes.active(Themes.FROST);
        Themes.active(Themes.FROST);
        Themes.active(Themes.OBSIDIAN);
        effect.dispose();

        assertEquals(List.of("obsidian", "frost", "obsidian"), observed);
    }

    /**
     * Every {@code public static final String} on {@link Tokens}, read reflectively — a hand-written
     * list would let a newly declared token slip past both themes unnoticed.
     */
    private static List<String> allTokenKeys() {
        List<String> keys = new ArrayList<>();
        for (Field field : Tokens.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && field.getType() == String.class) {
                keys.add(assertDoesNotThrow(() -> (String) field.get(null)));
            }
        }
        return keys;
    }
}
