package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/**
 * Custom additive sprite rendering pipeline.
 *
 * <p>
 * Uses {@link BlendFunction#ADDITIVE} (ONE, ONE) — pure additive blend
 * where the source alpha is completely irrelevant. This is critical for
 * Iris compatibility: even if the shader pack's substitute fragment shader
 * modifies the alpha channel, the blend result is unaffected because the
 * blend equation is {@code result = 1 * src + 1 * dst}.
 * </p>
 *
 * <p>
 * Contrast with {@code LIGHTNING} (SRC_ALPHA, ONE) where modified alpha
 * directly scales the source contribution, causing visible differences
 * between vanilla and Iris.
 * </p>
 */
public final class AdditiveSpriteRenderer {

    private AdditiveSpriteRenderer() {}

    private static RenderPipeline pipeline;
    private static final Map<ResourceLocation, RenderType> renderTypeCache = new HashMap<>();

    /**
     * The additive sprite pipeline:
     * <ul>
     *   <li>{@code BlendFunction.ADDITIVE} (ONE, ONE) — pure additive, alpha-independent</li>
     *   <li>{@code depthWrite = false} — no depth occlusion from transparent areas</li>
     *   <li>{@code cull = false} — billboard rendering</li>
     *   <li>Iris program: {@code EMISSIVE_ENTITIES} (gbuffers_spidereyes) — designed for additive/emissive content</li>
     * </ul>
     */
    public static RenderPipeline getPipeline() {
        if (pipeline == null) {
            pipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                            .withLocation(TestmodClient.id("pipeline/additive_sprite"))
                            .withVertexShader(TestmodClient.id("core/additive_sprite"))
                            .withFragmentShader(TestmodClient.id("core/additive_sprite"))
                            .withSampler("Sampler0")
                            .withSampler("Sampler1")
                            .withSampler("Sampler2")
                            .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                            .withCull(false)
                            .withDepthWrite(false)
                            .withBlend(BlendFunction.ADDITIVE)
                            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
                            .build()
            );
            // EMISSIVE_ENTITIES → gbuffers_spidereyes — designed for additive/emissive
            // rendering. Shader packs pass color through with minimal modification.
            RenderCompat.assignIrisProgram(pipeline, "EMISSIVE_ENTITIES");
        }
        return pipeline;
    }

    /**
     * Creates a RenderType for additive sprite rendering with the given texture.
     */
    public static RenderType getRenderType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false);
            return RenderType.create("glue_additive_sprite", 1536, false, true, getPipeline(), state);
        });
    }

    /**
     * Forces eager initialization of the pipeline.
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static void init() {
        getPipeline().hashCode();
        TestmodClient.LOGGER.info("Additive sprite pipeline initialized (blend=ADDITIVE, iris=EMISSIVE_ENTITIES)");
    }
}
