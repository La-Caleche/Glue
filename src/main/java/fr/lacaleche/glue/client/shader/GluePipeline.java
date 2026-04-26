package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GluePipeline {

    private final String name;
    private final RenderPipeline pipeline;
    private final boolean additive;
    private final Map<ResourceLocation, RenderType> renderTypeCache = new HashMap<>();

    private GluePipeline(String name, RenderPipeline pipeline, boolean additive) {
        this.name = name;
        this.pipeline = pipeline;
        this.additive = additive;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public String getName() {
        return name;
    }

    /**
     * Whether this pipeline uses additive blending (LIGHTNING or ADDITIVE).
     * Used by {@link ShadedBufferSource} to select the correct blit blend mode.
     */
    public boolean isAdditive() {
        return additive;
    }

    public static GluePipeline entity(ResourceLocation location,
            ResourceLocation vertShader,
            ResourceLocation fragShader) {
        return entity(location, vertShader, fragShader, BlendFunction.TRANSLUCENT);
    }

    public static GluePipeline entity(ResourceLocation location,
            ResourceLocation vertShader,
            ResourceLocation fragShader,
            BlendFunction blendFunction) {
        return entityCustom(location, vertShader, fragShader, blendFunction, "ENTITIES_TRANSLUCENT");
    }

    public static GluePipeline entityCustom(ResourceLocation location,
            ResourceLocation vertShader,
            ResourceLocation fragShader,
            BlendFunction blendFunction,
            String irisProgram) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                        "pipeline/" + location.getPath()))
                .withVertexShader(vertShader)
                .withFragmentShader(fragShader)
                .withSampler("Sampler0")
                .withSampler("Sampler1")
                .withSampler("Sampler2")
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withCull(false)
                .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS);

        if (blendFunction != null) {
            builder.withBlend(blendFunction);
        } else {
            builder.withoutBlend();
        }

        boolean isAdditive = blendFunction == BlendFunction.LIGHTNING
                || blendFunction == BlendFunction.ADDITIVE;

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        RenderCompat.assignIrisProgram(pipeline, irisProgram);

        return new GluePipeline(location.getPath(), pipeline, isAdditive);
    }

    public static GluePipeline block(ResourceLocation location,
            ResourceLocation vertShader,
            ResourceLocation fragShader) {
        RenderPipeline pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                                "pipeline/" + location.getPath()))
                        .withVertexShader(vertShader)
                        .withFragmentShader(fragShader)
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                        .build());
        RenderCompat.assignIrisProgram(pipeline, "TERRAIN");
        return new GluePipeline(location.getPath(), pipeline, false);
    }

    public static GluePipeline particle(ResourceLocation location,
            ResourceLocation vertShader,
            ResourceLocation fragShader) {
        RenderPipeline pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                                "pipeline/" + location.getPath()))
                        .withVertexShader(vertShader)
                        .withFragmentShader(fragShader)
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                        .build());
        RenderCompat.assignIrisProgram(pipeline, "PARTICLES");
        return new GluePipeline(location.getPath(), pipeline, false);
    }

    public RenderType entityType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, 1536, false, true, pipeline, state);
        });
    }

    public RenderType blockType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, 1536, false, true, pipeline, state);
        });
    }

    public RenderType particleType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, 1536, false, true, pipeline, state);
        });
    }

    public ShadedBufferSource wrap() {
        return new ShadedBufferSource(this);
    }

    /**
     * Creates a ShadedBufferSource that captures to an isolated FBO.
     * Use this when post-effects need to be applied to the captured
     * result before compositing to the main target.
     */
    public ShadedBufferSource wrapIsolated() {
        return new ShadedBufferSource(this, true);
    }
}
