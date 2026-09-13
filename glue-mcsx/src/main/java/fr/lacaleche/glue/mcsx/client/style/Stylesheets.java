package fr.lacaleche.glue.mcsx.client.style;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public final class Stylesheets {

    private static final ResourceLocation DEFAULT_ID = ResourceLocation.fromNamespaceAndPath(
            "glue",
            "mcsx/default"
    );
    private static final Stylesheet DEFAULT = StylesheetParser.parse(DEFAULT_ID, """
            Column {
                padding: 0px;
                gap: 8px;
                align-items: stretch;
                justify-content: start;
                background: transparent;
            }

            Row {
                padding: 0px;
                gap: 8px;
                align-items: center;
                justify-content: start;
                background: transparent;
            }

            Text {
                color: @text-primary;
                text-size: 13px;
            }

            .ui-heading {
                width: 100%;
                color: @text-primary;
                text-size: 20px;
            }

            .ui-copy {
                width: 100%;
                color: @text-explanatory;
                text-size: 13px;
            }

            .ui-screen {
                padding: 0px;
                gap: 0px;
                align-items: stretch;
                background: @dock-background;
            }

            .ui-viewport {
                width: 100%;
                padding: 24px;
                gap: 0px;
                align-items: center;
                justify-content: center;
                background: transparent;
            }

            .ui-card {
                width: 100%;
                max-width: 720px;
                padding: 24px;
                gap: 20px;
                align-items: stretch;
                background: @surface;
                corner-radius: @shell-radius;
            }

            .ui-section {
                width: 100%;
                padding: 0px;
                gap: 8px;
                align-items: stretch;
                background: transparent;
            }

            .ui-section-title {
                width: 100%;
                color: @text-label;
                text-size: 10px;
            }

            .ui-actions {
                width: 100%;
                padding: 0px;
                gap: 8px;
                align-items: stretch;
                background: transparent;
            }

            .ui-list {
                width: 100%;
                padding: 0px;
                gap: 8px;
                align-items: stretch;
                background: transparent;
            }

            Button {
                color: @text-on-accent;
                background: @accent;
                corner-radius: @corner-radius;
                control-height: @control-height;
                text-size: 13px;
                font-weight: bold;
                elevation: 2px;
                top-highlight: #1affffff;
                top-highlight-height: 1px;
            }

            Button:hover {
                background: @accent-hover;
            }

            Button:pressed {
                color: @text-on-accent;
                background: @surface-pressed;
                elevation: 0px;
                top-highlight: #80000000;
                top-highlight-height: 2px;
            }

            Button:disabled {
                color: @text-disabled;
                background: @surface-disabled;
                elevation: 0px;
                top-highlight: transparent;
                top-highlight-height: 0px;
            }

            Button.secondary {
                color: @text-muted;
                background: @surface-raised;
                font-weight: normal;
                top-highlight: #0dffffff;
            }

            Button.secondary:hover {
                color: @text-primary;
                background: @surface-hover;
            }

            Button.secondary:pressed {
                color: @text-muted;
                background: @surface-pressed;
                elevation: 0px;
                top-highlight: #80000000;
                top-highlight-height: 2px;
            }

            Button.secondary:disabled {
                color: @text-disabled;
                background: @surface-disabled;
                elevation: 0px;
                top-highlight: transparent;
                top-highlight-height: 0px;
            }

            Button.quiet {
                color: @text-muted;
                background: transparent;
                font-weight: normal;
                elevation: 0px;
                top-highlight: transparent;
                top-highlight-height: 0px;
            }

            Button.quiet:hover {
                color: @text-primary;
                background: @surface-quiet-hover;
            }

            Button.quiet:pressed {
                color: @text-muted;
                background: @surface-pressed;
                elevation: 0px;
                top-highlight: #80000000;
                top-highlight-height: 2px;
            }

            Button.quiet:disabled {
                color: @text-disabled;
                background: @surface-disabled;
                elevation: 0px;
                top-highlight: transparent;
                top-highlight-height: 0px;
            }

            Button.danger {
                color: @danger-text;
                background: @danger;
                font-weight: normal;
                top-highlight: #0dffffff;
            }

            Button.danger:hover {
                color: @danger-text-hover;
                background: @danger-hover;
            }

            Button.danger:pressed {
                color: @danger-text;
                background: @surface-pressed;
                elevation: 0px;
                top-highlight: #80000000;
                top-highlight-height: 2px;
            }

            Button.danger:disabled {
                color: @text-disabled;
                background: @surface-disabled;
                elevation: 0px;
                top-highlight: transparent;
                top-highlight-height: 0px;
            }

            TextField {
                color: @text-primary;
                hint-color: @text-muted;
                background: @surface-well;
                corner-radius: @corner-radius;
                control-height: @control-height;
                text-size: 13px;
                padding-horizontal: 13px;
                padding-vertical: 11px;
            }

            TextField:disabled {
                color: @text-disabled;
                background: @surface-disabled;
            }

            TextField:invalid {
                color: @danger-text;
                background: @danger-field;
            }

            Checkbox {
                color: @text-muted;
                indicator-tint: theme;
                control-height: @control-height;
                text-size: 13px;
            }

            Checkbox:hover {
                color: @text-primary;
            }

            Checkbox:checked {
                color: @text-primary;
            }

            Checkbox:disabled {
                color: @text-disabled;
            }
            """);
    private static final Map<ResourceLocation, ResourceValue> HANDLES = new LinkedHashMap<>();
    private static Map<ResourceLocation, Stylesheet> active = Map.of();
    private static long generation;

    private Stylesheets() {
    }

    public static synchronized Value<Stylesheet> resource(ResourceLocation id) {
        ResourceLocation required = Objects.requireNonNull(id, "id");
        return HANDLES.computeIfAbsent(
                required,
                ResourceValue::new
        );
    }

    /** Returns the built-in MCSX component baseline. */
    public static Stylesheet defaults() {
        return DEFAULT;
    }

    /** Places consumer rules above the built-in MCSX component baseline. */
    public static Stylesheet withDefaults(Stylesheet stylesheet) {
        Objects.requireNonNull(stylesheet, "stylesheet");
        if (stylesheet == DEFAULT || stylesheet == Stylesheet.empty()) return DEFAULT;
        if (stylesheet.includesDefaults()) return stylesheet;

        return Stylesheet.overlay(DEFAULT, stylesheet);
    }

    static void install(Map<ResourceLocation, Stylesheet> stylesheets) {
        Map<ResourceLocation, Stylesheet> installed = Map.copyOf(stylesheets);
        Map<ResourceLocation, ResourceValue> handles;
        synchronized (Stylesheets.class) {
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
            throw new IllegalStateException(
                    "One or more stylesheet consumers rejected a reload",
                    failure
            );
        }
    }

    private static final class ResourceValue implements Value<Stylesheet> {

        private final ResourceLocation id;
        private final Signal<Long> invalidation;

        private ResourceValue(ResourceLocation id) {
            this.id = id;
            this.invalidation = Signal.of(generation);
        }

        @Override
        public Stylesheet get() {
            synchronized (Stylesheets.class) {
                return active.getOrDefault(this.id, Stylesheet.empty());
            }
        }

        @Override
        public Subscription subscribe(Consumer<? super Stylesheet> listener) {
            Objects.requireNonNull(listener, "listener");
            return this.invalidation.subscribe(ignored -> listener.accept(this.get()));
        }

        private void publish() {
            this.invalidation.set(generation);
        }
    }
}
