package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.client.shader.pipeline.PipelineDefinitionLoader;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

/**
 * Custom additive sprite rendering pipeline.
 *
 * <p>The pipeline is now data-driven ({@code glue/pipelines/additive_sprite.json})
 * and resolved from the {@link PipelineDefinitionLoader} at render time.
 * A Java-built fallback is created eagerly in case the loader hasn't fired.</p>
 */
@Environment(EnvType.CLIENT)
public final class AdditiveSpriteRenderer {

    private static final ResourceLocation PIPELINE_ID = TestmodClient.id("additive_sprite");

    /** Java fallback — built eagerly during mod init. */
    private static GluePipeline fallbackPipeline;

    private AdditiveSpriteRenderer() {
    }

    /**
     * Returns the additive sprite pipeline, preferring the data-driven version.
     */
    public static GluePipeline getPipeline() {
        PipelineDefinitionLoader loader = PipelineDefinitionLoader.getInstance();
        if (loader != null) {
            GluePipeline dataDriven = loader.get(PIPELINE_ID);
            if (dataDriven != null) {
                return dataDriven;
            }
        }
        return fallbackPipeline;
    }

    /**
     * Forces eager initialization of the fallback pipeline.
     */
    public static void init() {
        fallbackPipeline = GluePipeline.builder(
                        PIPELINE_ID,
                        TestmodClient.id("core/additive_sprite"),
                        TestmodClient.id("core/additive_sprite"))
                .blend(BlendFunction.ADDITIVE)
                .irisProgram("EMISSIVE_ENTITIES")
                .build();
        TestmodClient.LOGGER.info("Additive sprite pipeline initialized (fallback ready)");
    }
}
