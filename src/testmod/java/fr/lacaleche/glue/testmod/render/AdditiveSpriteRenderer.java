package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.testmod.TestmodClient;

/**
 * Custom additive sprite rendering pipeline using Strategy B (capture/blit).
 *
 * <p>
 * Uses {@link GluePipeline} with {@link BlendFunction#ADDITIVE} (ONE, ONE).
 * Under Iris, {@link ShadedBufferSource} captures to a private additive FBO
 * with bypass enabled (our custom shader runs), then blits with additive
 * GL blending ({@code GL_ONE, GL_ONE}) — the black box bug is eliminated.
 * </p>
 */
public final class AdditiveSpriteRenderer {

    private AdditiveSpriteRenderer() {}

    private static GluePipeline pipeline;

    /**
     * The additive sprite pipeline. Uses {@code EMISSIVE_ENTITIES} as the
     * Iris program and {@code BlendFunction.ADDITIVE} for pure additive blend.
     */
    public static GluePipeline getPipeline() {
        if (pipeline == null) {
            pipeline = GluePipeline.entityCustom(
                    TestmodClient.id("additive_sprite"),
                    TestmodClient.id("core/additive_sprite"),
                    TestmodClient.id("core/additive_sprite"),
                    BlendFunction.ADDITIVE,
                    "EMISSIVE_ENTITIES"
            );
        }
        return pipeline;
    }

    /**
     * Forces eager initialization of the pipeline.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void init() {
        getPipeline().hashCode();
        TestmodClient.LOGGER.info("Additive sprite pipeline initialized (Strategy B, blend=ADDITIVE)");
    }
}
