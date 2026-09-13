package fr.lacaleche.glue.testmod.registries;

import com.mojang.blaze3d.pipeline.BlendFunction;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.effect.TimedEffectCodecs;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinition;
import fr.lacaleche.glue.registries.CoreShaderRegistry;
import fr.lacaleche.glue.registries.PostShaderRegistry;
import fr.lacaleche.glue.registries.TimedEffectRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;

import java.util.Optional;

/**
 * Demonstrates Glue's three shader registries:
 * <ul>
 *   <li>{@link CoreShaderRegistry#registerPipeline} — a {@link GluePipeline} (hologram) in the unified PIPELINES registry.</li>
 *   <li>{@link PostShaderRegistry#register} — post-processing {@link PostShaderHandle}s backed by {@code post_effect/*.json}.</li>
 *   <li>{@link TimedEffectRegistry#register} — a Java-defined {@link TimedEffectDefinition} registered through the same path as JSON timed effects.</li>
 * </ul>
 * See the data-driven counterparts under {@code resources/assets/glue-test/glue/}.
 */
public class TestShaders {

    public static final CoreShaderRegistry CORE = new CoreShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);
    public static final PostShaderRegistry POST = new PostShaderRegistry(TestmodClient.MOD_ID, TestmodClient::id);
    public static final TimedEffectRegistry TIMED = new TimedEffectRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    // Java GluePipeline (in Glue PIPELINES registry, alongside JSON pipelines)
    public static final GluePipeline HOLOGRAM = CORE.registerPipeline("hologram",
            TestmodClient.id("core/entity"),
            TestmodClient.id("core/hologram"),
            builder -> builder
                    .blend(BlendFunction.TRANSLUCENT)
                    .irisProgram("ENTITIES_TRANSLUCENT")
    );

    // Java-registered post chains (departure_vortex is the data-driven equivalent in glue/post_chains/)
    public static final PostShaderHandle GRAYSCALE = POST.register("grayscale");
    public static final PostShaderHandle BLUR = POST.register("blur");
    public static final PostShaderHandle SHATTERED_SCREEN = POST.register("shattered_screen");
    public static final PostShaderHandle END_LOCKED_PULSE = POST.register("end_locked_pulse");
    public static final PostShaderHandle CHROMATIC_ABERRATION = POST.register("chromatic_aberration");
    public static final PostShaderHandle IMPACT_FRAME = POST.register("impact_frame");

    // A timed effect defined in Java but reached through the same registry lookup as the JSON ones.
    // It mirrors TestPostShaderHandler.CHROMATIC so the two paths render the same effect: the shader
    // reads Strength as a normalized envelope, and the aberration must decay rather than cut off.
    public static final TimedEffectDefinition CHROMATIC_TIMED = TIMED.register("chromatic",
            new TimedEffectDefinition(
                    TestmodClient.id("chromatic_aberration"),
                    "ChromaticConfig",
                    4,
                    15,
                    Optional.of(TimedEffectCodecs.CurveKey.REVERSE)
            )
    );

    public static void registerShaders() {
        TestmodClient.LOGGER.info("Registered showcase shader pipelines and {} post chains",
                POST.getHandles().size());
    }
}
