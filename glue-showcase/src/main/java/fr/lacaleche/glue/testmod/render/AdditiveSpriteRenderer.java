package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

/**
 * Resolves the additive sprite pipeline: data-driven with a Java fallback.
 *
 * <p>{@link #getPipeline()} looks up {@code glue-test:additive_sprite} in the unified
 * {@link GlueClientRegistries#PIPELINES} registry, registering the Java-built twin of
 * {@code glue/pipelines/additive_sprite.json} on a miss; the JSON definition overrides
 * it whenever the loader has it. {@link #init()} performs the first resolution during
 * mod init, before resources load, so the fallback is built eagerly.</p>
 */
@Environment(EnvType.CLIENT)
public final class AdditiveSpriteRenderer {

    private static final ResourceLocation PIPELINE_ID = TestmodClient.id("additive_sprite");

    private AdditiveSpriteRenderer() {
    }

    /** Returns the additive sprite pipeline: the JSON definition once loaded, the Java fallback before. */
    public static GluePipeline getPipeline() {
        return GlueClientRegistries.PIPELINES.getOrRegister(PIPELINE_ID, AdditiveSpriteRenderer::buildFallback);
    }

    /** Registers the fallback eagerly so the first rendered frame does not pay the pipeline build. */
    public static void init() {
        getPipeline();
    }

    /** Mirrors {@code glue/pipelines/additive_sprite.json} field for field — keep the two in sync. */
    private static GluePipeline buildFallback() {
        return GluePipeline.builder(
                        PIPELINE_ID,
                        TestmodClient.id("core/additive_sprite"),
                        TestmodClient.id("core/additive_sprite"))
                .blend(BlendFunction.ADDITIVE)
                .alphaCutout(0.1f)
                .cull(false)
                .samplers("Sampler0")
                .category(GluePipeline.PipelineCategory.ENTITY)
                .irisProgram("EMISSIVE_ENTITIES")
                .build();
    }
}
