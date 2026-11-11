package fr.lacaleche.glue.testmod.ui;

import fr.lacaleche.glue.client.options.GraphicsOptions;
import fr.lacaleche.glue.client.options.GraphicsOptionsPage;
import fr.lacaleche.glue.client.options.OptionImpact;
import net.minecraft.network.chat.Component;

import java.util.Arrays;
import java.util.Locale;

/**
 * The showcase's graphics settings, the example for a mod adding its own: a button at the foot of the video
 * settings opens them, and with Sodium installed they are also a page of Sodium's settings screen. The settings
 * are one record; a preset row sets them all, and reads "Custom" when they match none. They live in memory only
 * here, where a mod would save them to its config file in {@code apply}.
 */
public final class ShowcaseGraphicsOptions {

    private static Settings settings = Preset.MEDIUM.settings;

    private ShowcaseGraphicsOptions() {
    }

    /** Registers the page; called during client initialisation. */
    public static void register() {
        GraphicsOptions.register(GraphicsOptionsPage.<Settings>builder("glue-test.options",
                        () -> settings, next -> settings = next)
                .section()
                .level("glue-test.options.preset", Preset::label, Preset::cycle)
                .section(Component.translatable("glue-test.options.group.sparks"))
                .slider("glue-test.options.sparks", 64, 1024, 64,
                        sparks -> Component.translatable("glue-test.options.sparks.value", sparks),
                        Settings::sparks, Settings::withSparks, OptionImpact.HIGH)
                .toggle("glue-test.options.trails", Settings::trails, Settings::withTrails, OptionImpact.MEDIUM).half()
                .choice("glue-test.options.shape", Shape.class, Shape::label, Settings::shape, Settings::withShape,
                        OptionImpact.LOW).half()
                .build());
    }

    /** The settings, immutable: each row writes its value into a copy. */
    public record Settings(int sparks, boolean trails, Shape shape) {

        Settings withSparks(int value) {
            return new Settings(value, trails, shape);
        }

        Settings withTrails(boolean value) {
            return new Settings(sparks, value, shape);
        }

        Settings withShape(Shape value) {
            return new Settings(sparks, trails, value);
        }
    }

    public enum Shape {
        DOT,
        STAR;

        Component label() {
            return Component.translatable("glue-test.options.shape." + name().toLowerCase(Locale.ROOT));
        }
    }

    private enum Preset {
        LOW(new Settings(128, false, Shape.DOT)),
        MEDIUM(new Settings(512, true, Shape.DOT)),
        HIGH(new Settings(1024, true, Shape.STAR));

        private final Settings settings;

        Preset(Settings settings) {
            this.settings = settings;
        }

        static Component label(Settings settings) {
            return Arrays.stream(values()).filter(preset -> preset.settings.equals(settings)).findFirst()
                    .map(preset -> Component.translatable("glue-test.options.preset." + preset.name()
                            .toLowerCase(Locale.ROOT)))
                    .orElse(Component.translatable("glue-test.options.preset.custom"));
        }

        /** The next preset, or the previous one going {@code backward}; from Custom, the first or the last. */
        static Settings cycle(Settings settings, boolean backward) {
            Preset[] presets = values();
            int at = Arrays.stream(presets).map(preset -> preset.settings).toList().indexOf(settings);
            int next = at < 0 ? (backward ? presets.length - 1 : 0)
                    : Math.floorMod(at + (backward ? -1 : 1), presets.length);
            return presets[next].settings;
        }
    }
}
