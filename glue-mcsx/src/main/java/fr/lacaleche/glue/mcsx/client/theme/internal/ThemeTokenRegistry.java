package fr.lacaleche.glue.mcsx.client.theme.internal;

import fr.lacaleche.glue.mcsx.client.theme.ThemeTokens;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ThemeTokenRegistry {

    private static final List<Entry> ENTRIES = validate(List.of(
            color(ThemeTokens.SURFACE),
            color(ThemeTokens.SURFACE_RAISED),
            color(ThemeTokens.SURFACE_WELL),
            color(ThemeTokens.SURFACE_HEADER),
            color(ThemeTokens.SURFACE_FOOTER),
            color(ThemeTokens.SURFACE_HOVER),
            color(ThemeTokens.SURFACE_QUIET_HOVER),
            color(ThemeTokens.SURFACE_PRESSED),
            color(ThemeTokens.SURFACE_DISABLED),
            color(ThemeTokens.TEXT_PRIMARY),
            color(ThemeTokens.TEXT_MUTED),
            color(ThemeTokens.TEXT_EXPLANATORY),
            color(ThemeTokens.TEXT_SUBTITLE),
            color(ThemeTokens.TEXT_META),
            color(ThemeTokens.TEXT_LABEL),
            color(ThemeTokens.TEXT_DISABLED),
            color(ThemeTokens.TEXT_ON_ACCENT),
            color(ThemeTokens.TEXT_ON_LIGHT),
            color(ThemeTokens.ACCENT),
            color(ThemeTokens.ACCENT_HOVER),
            color(ThemeTokens.CONTROL_ON),
            color(ThemeTokens.DANGER),
            color(ThemeTokens.DANGER_HOVER),
            color(ThemeTokens.DANGER_FIELD),
            color(ThemeTokens.DANGER_TEXT),
            color(ThemeTokens.DANGER_TEXT_HOVER),
            color(ThemeTokens.DANGER_LINE),
            color(ThemeTokens.HUD_SURFACE),
            color(ThemeTokens.HUD_SURFACE_STRONG),
            dimension(ThemeTokens.CONTROL_HEIGHT),
            dimension(ThemeTokens.CORNER_RADIUS),
            dimension(ThemeTokens.CHIP_RADIUS),
            dimension(ThemeTokens.SHELL_RADIUS),
            color(ThemeTokens.DOCK_BACKGROUND),
            color(ThemeTokens.DOCK_PANE_BACKGROUND),
            color(ThemeTokens.DOCK_HEADER_BACKGROUND),
            color(ThemeTokens.DOCK_BORDER),
            color(ThemeTokens.DOCK_ACTIVE_TAB_BACKGROUND),
            color(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND),
            color(ThemeTokens.DOCK_DROP_HIGHLIGHT),
            dockMetrics(ThemeTokens.DOCK_METRICS)
    ));
    private static final Map<String, Entry> BY_NAME = byName(ENTRIES);

    private ThemeTokenRegistry() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static Entry find(String name) {
        return BY_NAME.get(name);
    }

    static List<Entry> validate(List<Entry> entries) {
        Map<String, Entry> names = new LinkedHashMap<>();
        Map<Token<?>, Entry> tokens = new IdentityHashMap<>();
        for (Entry entry : entries) {
            if (names.put(entry.name(), entry) != null) {
                throw new IllegalStateException("Duplicate theme token name '" + entry.name() + "'");
            }
            if (tokens.put(entry.token(), entry) != null) {
                throw new IllegalStateException("Duplicate theme token instance '" + entry.name() + "'");
            }
        }
        return List.copyOf(entries);
    }

    private static Entry color(Token<Integer> token) {
        return new Entry(token.name(), token, Kind.COLOR, token);
    }

    private static Entry dimension(Token<Integer> token) {
        return new Entry(token.name(), token, Kind.DIMENSION, token);
    }

    private static Entry dockMetrics(Token<?> token) {
        return new Entry(token.name(), token, Kind.DOCK_METRICS, null);
    }

    private static Map<String, Entry> byName(List<Entry> entries) {
        Map<String, Entry> result = new LinkedHashMap<>();
        for (Entry entry : entries) {
            result.put(entry.name(), entry);
        }
        return Map.copyOf(result);
    }

    public enum Kind {
        COLOR,
        DIMENSION,
        DOCK_METRICS
    }

    public record Entry(String name, Token<?> token, Kind kind, Token<Integer> integerToken) {

        public Entry {
            if ((kind == Kind.DOCK_METRICS) == (integerToken != null)) {
                throw new IllegalArgumentException("Theme token scalar type does not match kind");
            }
        }
    }
}
