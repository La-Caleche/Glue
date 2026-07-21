package fr.lacaleche.glue.mcsx.core.theme;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A named palette: each {@link Tokens} key maps to a packed ARGB color (alpha in the high byte, so
 * several surface/border/scrim tokens are deliberately semi-transparent). Colors-only by design —
 * radii and spacing are fixed constants in the styling layer, not tokens.
 */
public record Theme(String name, Map<String, Integer> colors) {

    /** Sentinel returned for an unknown token: loud magenta, so a missing token is obvious on screen. */
    public static final int MISSING = 0xFFFF00FF;

    public Theme {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("theme name must not be blank");
        }
        colors = Map.copyOf(colors);
    }

    public int color(String key) {
        Integer value = colors.get(key);
        return value != null ? value : MISSING;
    }

    /** Creates a complete derived palette by replacing selected colors in this theme. */
    public Theme withOverrides(String derivedName, Map<String, Integer> overrides) {
        Map<String, Integer> derived = new HashMap<>(colors);
        derived.putAll(Objects.requireNonNull(overrides, "overrides"));
        return new Theme(derivedName, derived);
    }
}
