package fr.lacaleche.glue.testmod.mcsx.studio;

import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * One entry in the Effects pane. {@code trigger} and {@code active} touch the post-effect renderer,
 * so both are invoked on Minecraft's client thread &mdash; never from the UI thread.
 *
 * <p>The six timed entries cover every registration path the showcase demonstrates: a JSON effect on
 * a JSON chain, a JSON effect on a Java chain, the Java-registered {@code TimedEffectDefinition}
 * &mdash; reached through the same registry lookup as the JSON ones, which is the whole point of
 * that path &mdash; and three fully Java-built {@code TimedPostEffect}s. The two toggles are plain
 * on/off handles.</p>
 */
record StudioEffect(String id, String labelKey, boolean toggle, Runnable trigger, BooleanSupplier active) {

    static List<StudioEffect> all() {
        TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
        return List.of(
                registry("vortex", "mcsx.studio.effect.vortex", TestmodClient.id("departure_vortex")),
                registry("pulse", "mcsx.studio.effect.pulse", TestmodClient.id("denial_pulse")),
                registry("chromatic_registry", "mcsx.studio.effect.chromatic_registry",
                        TestmodClient.id("chromatic")),
                new StudioEffect("chromatic", "mcsx.studio.effect.chromatic", false,
                        TestPostShaderHandler.CHROMATIC::trigger, TestPostShaderHandler.CHROMATIC::isActive),
                new StudioEffect("shattered", "mcsx.studio.effect.shattered", false,
                        TestPostShaderHandler.SHATTERED::trigger, TestPostShaderHandler.SHATTERED::isActive),
                new StudioEffect("impact", "mcsx.studio.effect.impact", false,
                        TestPostShaderHandler.IMPACT::trigger, TestPostShaderHandler.IMPACT::isActive),
                new StudioEffect("blur", "mcsx.studio.effect.blur", true,
                        () -> effects.toggleByHandle(TestShaders.BLUR),
                        () -> effects.isToggled(TestShaders.BLUR)),
                new StudioEffect("grayscale", "mcsx.studio.effect.grayscale", true,
                        () -> effects.toggleByHandle(TestShaders.GRAYSCALE),
                        () -> effects.isToggled(TestShaders.GRAYSCALE))
        );
    }

    private static StudioEffect registry(String id, String labelKey, ResourceLocation effect) {
        TestPostShaderHandler effects = TestPostShaderHandler.INSTANCE;
        return new StudioEffect(id, labelKey, false,
                () -> effects.triggerFromRegistry(effect),
                () -> effects.isRegistryActive(effect));
    }
}
