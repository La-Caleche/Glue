package fr.lacaleche.glue.mcsx.client.theme;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeDefinition;
import fr.lacaleche.glue.mcsx.client.theme.internal.ThemeJsonParser;
import icyllis.modernui.graphics.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class Themes {

    private static final Map<ResourceLocation, ResourceValue> HANDLES = new LinkedHashMap<>();
    private static Map<ResourceLocation, Theme> active = Map.of();
    private static long generation;
    private static final Theme MCSX = Theme.builder()
            .set(ThemeTokens.SURFACE, ThemeTokens.SURFACE.fallback())
            .set(ThemeTokens.SURFACE_RAISED, ThemeTokens.SURFACE_RAISED.fallback())
            .set(ThemeTokens.SURFACE_WELL, ThemeTokens.SURFACE_WELL.fallback())
            .set(ThemeTokens.SURFACE_HEADER, ThemeTokens.SURFACE_HEADER.fallback())
            .set(ThemeTokens.SURFACE_FOOTER, ThemeTokens.SURFACE_FOOTER.fallback())
            .set(ThemeTokens.SURFACE_HOVER, ThemeTokens.SURFACE_HOVER.fallback())
            .set(ThemeTokens.SURFACE_QUIET_HOVER, ThemeTokens.SURFACE_QUIET_HOVER.fallback())
            .set(ThemeTokens.SURFACE_PRESSED, ThemeTokens.SURFACE_PRESSED.fallback())
            .set(ThemeTokens.SURFACE_DISABLED, ThemeTokens.SURFACE_DISABLED.fallback())
            .set(ThemeTokens.TEXT_PRIMARY, ThemeTokens.TEXT_PRIMARY.fallback())
            .set(ThemeTokens.TEXT_MUTED, ThemeTokens.TEXT_MUTED.fallback())
            .set(ThemeTokens.TEXT_EXPLANATORY, ThemeTokens.TEXT_EXPLANATORY.fallback())
            .set(ThemeTokens.TEXT_SUBTITLE, ThemeTokens.TEXT_SUBTITLE.fallback())
            .set(ThemeTokens.TEXT_META, ThemeTokens.TEXT_META.fallback())
            .set(ThemeTokens.TEXT_LABEL, ThemeTokens.TEXT_LABEL.fallback())
            .set(ThemeTokens.TEXT_DISABLED, ThemeTokens.TEXT_DISABLED.fallback())
            .set(ThemeTokens.TEXT_ON_ACCENT, ThemeTokens.TEXT_ON_ACCENT.fallback())
            .set(ThemeTokens.TEXT_ON_LIGHT, ThemeTokens.TEXT_ON_LIGHT.fallback())
            .set(ThemeTokens.ACCENT, ThemeTokens.ACCENT.fallback())
            .set(ThemeTokens.ACCENT_HOVER, ThemeTokens.ACCENT_HOVER.fallback())
            .set(ThemeTokens.CONTROL_ON, ThemeTokens.CONTROL_ON.fallback())
            .set(ThemeTokens.DANGER, ThemeTokens.DANGER.fallback())
            .set(ThemeTokens.DANGER_HOVER, ThemeTokens.DANGER_HOVER.fallback())
            .set(ThemeTokens.DANGER_FIELD, ThemeTokens.DANGER_FIELD.fallback())
            .set(ThemeTokens.DANGER_TEXT, ThemeTokens.DANGER_TEXT.fallback())
            .set(ThemeTokens.DANGER_TEXT_HOVER, ThemeTokens.DANGER_TEXT_HOVER.fallback())
            .set(ThemeTokens.DANGER_LINE, ThemeTokens.DANGER_LINE.fallback())
            .set(ThemeTokens.HUD_SURFACE, ThemeTokens.HUD_SURFACE.fallback())
            .set(ThemeTokens.HUD_SURFACE_STRONG, ThemeTokens.HUD_SURFACE_STRONG.fallback())
            .set(ThemeTokens.CHECKBOX_INDICATOR, ThemeTokens.checkboxIndicator(
                    ThemeTokens.CONTROL_ON.fallback(),
                    ThemeTokens.TEXT_PRIMARY.fallback(),
                    ThemeTokens.TEXT_MUTED.fallback()
            ))
            .set(ThemeTokens.CONTROL_HEIGHT, ThemeTokens.CONTROL_HEIGHT.fallback())
            .set(ThemeTokens.CORNER_RADIUS, ThemeTokens.CORNER_RADIUS.fallback())
            .set(ThemeTokens.CHIP_RADIUS, ThemeTokens.CHIP_RADIUS.fallback())
            .set(ThemeTokens.SHELL_RADIUS, ThemeTokens.SHELL_RADIUS.fallback())
            .set(ThemeTokens.DOCK_BACKGROUND, ThemeTokens.DOCK_BACKGROUND.fallback())
            .set(ThemeTokens.DOCK_PANE_BACKGROUND, ThemeTokens.DOCK_PANE_BACKGROUND.fallback())
            .set(ThemeTokens.DOCK_HEADER_BACKGROUND, ThemeTokens.DOCK_HEADER_BACKGROUND.fallback())
            .set(ThemeTokens.DOCK_BORDER, ThemeTokens.DOCK_BORDER.fallback())
            .set(ThemeTokens.DOCK_ACTIVE_TAB_BACKGROUND, ThemeTokens.DOCK_ACTIVE_TAB_BACKGROUND.fallback())
            .set(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND, ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND.fallback())
            .set(ThemeTokens.DOCK_DROP_HIGHLIGHT, ThemeTokens.DOCK_DROP_HIGHLIGHT.fallback())
            .set(ThemeTokens.DOCK_METRICS, ThemeTokens.DOCK_METRICS.fallback())
            .build();
    private static final Theme LIGHT = Theme.builder()
            .set(ThemeTokens.SURFACE, Color.rgb(244, 246, 248))
            .set(ThemeTokens.SURFACE_RAISED, Color.WHITE)
            .set(ThemeTokens.SURFACE_WELL, Color.rgb(225, 229, 233))
            .set(ThemeTokens.SURFACE_HEADER, Color.rgb(238, 241, 244))
            .set(ThemeTokens.SURFACE_FOOTER, Color.rgb(231, 235, 238))
            .set(ThemeTokens.SURFACE_HOVER, Color.rgb(248, 249, 250))
            .set(ThemeTokens.SURFACE_QUIET_HOVER, Color.rgb(231, 234, 237))
            .set(ThemeTokens.SURFACE_PRESSED, Color.rgb(217, 222, 227))
            .set(ThemeTokens.SURFACE_DISABLED, Color.rgb(231, 234, 237))
            .set(ThemeTokens.TEXT_PRIMARY, Color.rgb(28, 32, 42))
            .set(ThemeTokens.TEXT_MUTED, Color.rgb(85, 94, 112))
            .set(ThemeTokens.TEXT_EXPLANATORY, Color.rgb(100, 108, 119))
            .set(ThemeTokens.TEXT_SUBTITLE, Color.rgb(112, 120, 130))
            .set(ThemeTokens.TEXT_META, Color.rgb(126, 134, 143))
            .set(ThemeTokens.TEXT_LABEL, Color.rgb(139, 146, 154))
            .set(ThemeTokens.TEXT_DISABLED, Color.rgb(163, 169, 175))
            .set(ThemeTokens.TEXT_ON_ACCENT, Color.WHITE)
            .set(ThemeTokens.TEXT_ON_LIGHT, Color.WHITE)
            .set(ThemeTokens.ACCENT, Color.rgb(82, 92, 102))
            .set(ThemeTokens.ACCENT_HOVER, Color.rgb(67, 76, 85))
            .set(ThemeTokens.CONTROL_ON, Color.rgb(52, 59, 67))
            .set(ThemeTokens.DANGER, Color.rgb(247, 230, 227))
            .set(ThemeTokens.DANGER_HOVER, Color.rgb(241, 218, 214))
            .set(ThemeTokens.DANGER_FIELD, Color.rgb(252, 242, 240))
            .set(ThemeTokens.DANGER_TEXT, Color.rgb(132, 68, 58))
            .set(ThemeTokens.DANGER_TEXT_HOVER, Color.rgb(103, 47, 39))
            .set(ThemeTokens.DANGER_LINE, Color.rgb(156, 91, 80))
            .set(ThemeTokens.HUD_SURFACE, ThemeTokens.withAlpha(Color.rgb(244, 246, 248), 224))
            .set(ThemeTokens.HUD_SURFACE_STRONG, ThemeTokens.withAlpha(Color.rgb(225, 229, 233), 199))
            .set(ThemeTokens.CHECKBOX_INDICATOR, ThemeTokens.checkboxIndicator(
                    Color.rgb(52, 59, 67),
                    Color.rgb(28, 32, 42),
                    Color.rgb(85, 94, 112)
            ))
            .set(ThemeTokens.CONTROL_HEIGHT, 32)
            .set(ThemeTokens.CORNER_RADIUS, 3)
            .set(ThemeTokens.CHIP_RADIUS, 2)
            .set(ThemeTokens.SHELL_RADIUS, 4)
            .set(ThemeTokens.DOCK_BACKGROUND, Color.rgb(217, 222, 227))
            .set(ThemeTokens.DOCK_PANE_BACKGROUND, Color.rgb(244, 246, 248))
            .set(ThemeTokens.DOCK_HEADER_BACKGROUND, Color.rgb(238, 241, 244))
            .set(ThemeTokens.DOCK_BORDER, Color.rgb(225, 229, 233))
            .set(ThemeTokens.DOCK_ACTIVE_TAB_BACKGROUND, Color.WHITE)
            .set(ThemeTokens.DOCK_DRAG_GHOST_BACKGROUND, ThemeTokens.withAlpha(Color.WHITE, 232))
            .set(ThemeTokens.DOCK_DROP_HIGHLIGHT, ThemeTokens.withAlpha(Color.rgb(52, 59, 67), 92))
            .set(ThemeTokens.DOCK_METRICS, new DockMetrics(12, 12, 36, 4, 0, 11, 10, 14, 8, 32, 32))
            .build();

    private Themes() {
    }

    /** The canonical MCSX default theme. */
    public static Theme mcsx() {
        return MCSX;
    }

    /**
     * Compatibility name for the former dark preset. MCSX is now the only built-in dark design.
     */
    public static Theme dark() {
        return MCSX;
    }

    public static Theme light() {
        return LIGHT;
    }

    /**
     * Returns a stable live handle for a JSON theme. Missing and removed resources resolve to MCSX.
     */
    public static synchronized Value<Theme> resource(ResourceLocation id) {
        ResourceLocation required = Objects.requireNonNull(id, "id");
        return HANDLES.computeIfAbsent(
                required,
                ResourceValue::new
        );
    }

    /**
     * Validates one theme JSON document without installing it, applying the reload-time
     * per-document checks: syntax, schema fields, token names and kinds, value formats, and
     * reference cycles among the document's own values. Inheritance is not resolved here, so a
     * missing or cyclic {@code parent} chain is still detected only when a complete resource
     * generation reloads — the entry point for validating a shipped theme resource in an
     * ordinary unit test.
     *
     * @throws IllegalArgumentException naming the resource and reason when the document is invalid
     */
    public static void validate(ResourceLocation id, Reader reader) {
        ResourceLocation required = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(reader, "reader");
        ThemeDefinition definition = ThemeJsonParser.parse(required, reader);
        // Resolving without the parent checks the document's own references against the built-in
        // baseline: a cycle among own values is a cycle under any parent, and baseline literals
        // terminate every other chain, so this cannot reject a document a reload would accept.
        ThemeResolver.resolve(Map.of(required, new ThemeDefinition(null, definition.values())));
    }

    static void install(Map<ResourceLocation, Theme> themes) {
        Map<ResourceLocation, Theme> installed = Map.copyOf(themes);
        Map<ResourceLocation, ResourceValue> handles;
        synchronized (Themes.class) {
            active = installed;
            generation++;
            handles = Map.copyOf(HANDLES);
        }
        RuntimeException failure = null;
        for (Map.Entry<ResourceLocation, ResourceValue> entry : handles.entrySet()) {
            try {
                entry.getValue().publish();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw new IllegalStateException("One or more theme consumers rejected a reload", failure);
        }
    }

    private static final class ResourceValue implements Value<Theme> {

        private final ResourceLocation id;
        private final Signal<Long> invalidation;

        private ResourceValue(ResourceLocation id) {
            this.id = id;
            this.invalidation = Signal.of(generation);
        }

        @Override
        public Theme get() {
            synchronized (Themes.class) {
                return active.getOrDefault(this.id, MCSX);
            }
        }

        @Override
        public Subscription subscribe(Consumer<? super Theme> listener) {
            Objects.requireNonNull(listener, "listener");
            return this.invalidation.subscribe(ignored -> listener.accept(this.get()));
        }

        private void publish() {
            this.invalidation.set(generation);
        }
    }
}
