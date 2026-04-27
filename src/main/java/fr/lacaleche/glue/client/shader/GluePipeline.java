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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class GluePipeline {

    private static final int DEFAULT_BUFFER_SIZE = 1536;
    private final String name;
    private final RenderPipeline pipeline;
    private final boolean additive;
    private final PipelineCategory category;
    private final Map<ResourceLocation, RenderType> renderTypeCache = new HashMap<>();

    private GluePipeline(String name, RenderPipeline pipeline, boolean additive, PipelineCategory category) {
        this.name = name;
        this.pipeline = pipeline;
        this.additive = additive;
        this.category = category;
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

        return new GluePipeline(location.getPath(), pipeline, isAdditive, PipelineCategory.ENTITY);
    }

    public static GluePipeline block(ResourceLocation location,
                                     ResourceLocation vertShader,
                                     ResourceLocation fragShader) {
        return block(location, vertShader, fragShader, BlendFunction.TRANSLUCENT, "TERRAIN");
    }

    public static GluePipeline block(ResourceLocation location,
                                     ResourceLocation vertShader,
                                     ResourceLocation fragShader,
                                     BlendFunction blendFunction,
                                     String irisProgram) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                        "pipeline/" + location.getPath()))
                .withVertexShader(vertShader)
                .withFragmentShader(fragShader)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS);

        if (blendFunction != null) {
            builder.withBlend(blendFunction);
        } else {
            builder.withoutBlend();
        }

        boolean isAdditive = blendFunction == BlendFunction.LIGHTNING
                || blendFunction == BlendFunction.ADDITIVE;

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        RenderCompat.assignIrisProgram(pipeline, irisProgram);
        return new GluePipeline(location.getPath(), pipeline, isAdditive, PipelineCategory.BLOCK);
    }

    public static GluePipeline particle(ResourceLocation location,
                                        ResourceLocation vertShader,
                                        ResourceLocation fragShader) {
        return particle(location, vertShader, fragShader, BlendFunction.TRANSLUCENT, "PARTICLES");
    }

    public static GluePipeline particle(ResourceLocation location,
                                        ResourceLocation vertShader,
                                        ResourceLocation fragShader,
                                        BlendFunction blendFunction,
                                        String irisProgram) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                        "pipeline/" + location.getPath()))
                .withVertexShader(vertShader)
                .withFragmentShader(fragShader)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS);

        if (blendFunction != null) {
            builder.withBlend(blendFunction);
        } else {
            builder.withoutBlend();
        }

        boolean isAdditive = blendFunction == BlendFunction.LIGHTNING
                || blendFunction == BlendFunction.ADDITIVE;

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        RenderCompat.assignIrisProgram(pipeline, irisProgram);
        return new GluePipeline(location.getPath(), pipeline, isAdditive, PipelineCategory.PARTICLE);
    }

    /**
     * Creates a builder for a fully customizable pipeline.
     * Use this when the convenience factory methods ({@link #entity}, {@link #block},
     * {@link #particle}) don't offer enough control.
     *
     * <p>Defaults match the {@link #entity} preset:
     * <ul>
     *   <li>Snippet: {@code MATRICES_FOG_LIGHT_DIR_SNIPPET}</li>
     *   <li>Vertex format: {@code NEW_ENTITY / QUADS}</li>
     *   <li>Blend: {@code TRANSLUCENT}</li>
     *   <li>Samplers: {@code Sampler0, Sampler1, Sampler2}</li>
     *   <li>Alpha cutout: {@code 0.1}</li>
     *   <li>Cull: {@code false}</li>
     *   <li>Iris program: {@code ENTITIES_TRANSLUCENT}</li>
     *   <li>Category: {@code ENTITY}</li>
     * </ul>
     */
    public static Builder builder(ResourceLocation location,
                                  ResourceLocation vertShader,
                                  ResourceLocation fragShader) {
        return new Builder(location, vertShader, fragShader);
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

    public PipelineCategory getCategory() {
        return category;
    }

    /**
     * Returns the correct {@link RenderType} for this pipeline's category.
     * Dispatches to {@link #entityType}, {@link #blockType}, or {@link #particleType}
     * based on the category set at construction time.
     */
    public RenderType renderType(ResourceLocation texture) {
        return switch (category) {
            case ENTITY -> entityType(texture);
            case BLOCK -> blockType(texture);
            case PARTICLE -> particleType(texture);
        };
    }

    public RenderType entityType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
        });
    }

    public RenderType blockType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
        });
    }

    public RenderType particleType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
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

    /**
     * The category of geometry this pipeline is designed for.
     * Determines which {@link RenderType} factory is used by {@link #renderType(ResourceLocation)}.
     */
    public enum PipelineCategory {
        ENTITY, BLOCK, PARTICLE
    }

    public static class Builder {

        private final ResourceLocation location;
        private final ResourceLocation vertShader;
        private final ResourceLocation fragShader;

        private RenderPipeline.Snippet snippet = RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET;
        private VertexFormat vertexFormat = DefaultVertexFormat.NEW_ENTITY;
        private VertexFormat.Mode vertexMode = VertexFormat.Mode.QUADS;
        private BlendFunction blendFunction = BlendFunction.TRANSLUCENT;
        private boolean cull = false;
        private float alphaCutout = 0.1f;
        private String irisProgram = "ENTITIES_TRANSLUCENT";
        private PipelineCategory category = PipelineCategory.ENTITY;
        private List<String> samplers = new ArrayList<>(List.of("Sampler0", "Sampler1", "Sampler2"));

        private Builder(ResourceLocation location, ResourceLocation vertShader, ResourceLocation fragShader) {
            this.location = location;
            this.vertShader = vertShader;
            this.fragShader = fragShader;
        }

        public Builder snippet(RenderPipeline.Snippet snippet) {
            this.snippet = snippet;
            return this;
        }

        public Builder vertexFormat(VertexFormat format, VertexFormat.Mode mode) {
            this.vertexFormat = format;
            this.vertexMode = mode;
            return this;
        }

        public Builder blend(BlendFunction blend) {
            this.blendFunction = blend;
            return this;
        }

        public Builder noBlend() {
            this.blendFunction = null;
            return this;
        }

        public Builder cull(boolean cull) {
            this.cull = cull;
            return this;
        }

        public Builder alphaCutout(float cutout) {
            this.alphaCutout = cutout;
            return this;
        }

        public Builder irisProgram(String program) {
            this.irisProgram = program;
            return this;
        }

        public Builder category(PipelineCategory category) {
            this.category = category;
            return this;
        }

        /**
         * Replaces the default sampler list entirely.
         */
        public Builder samplers(String... samplers) {
            this.samplers = new ArrayList<>(List.of(samplers));
            return this;
        }

        /**
         * Adds a sampler to the existing list.
         */
        public Builder addSampler(String sampler) {
            this.samplers.add(sampler);
            return this;
        }

        public GluePipeline build() {
            RenderPipeline.Builder pipelineBuilder = RenderPipeline.builder(snippet)
                    .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                            "pipeline/" + location.getPath()))
                    .withVertexShader(vertShader)
                    .withFragmentShader(fragShader)
                    .withShaderDefine("ALPHA_CUTOUT", alphaCutout)
                    .withCull(cull)
                    .withVertexFormat(vertexFormat, vertexMode);

            for (String sampler : samplers) {
                pipelineBuilder.withSampler(sampler);
            }

            if (blendFunction != null) {
                pipelineBuilder.withBlend(blendFunction);
            } else {
                pipelineBuilder.withoutBlend();
            }

            boolean isAdditive = blendFunction == BlendFunction.LIGHTNING
                    || blendFunction == BlendFunction.ADDITIVE;

            RenderPipeline pipeline = RenderPipelines.register(pipelineBuilder.build());
            RenderCompat.assignIrisProgram(pipeline, irisProgram);

            return new GluePipeline(location.getPath(), pipeline, isAdditive, category);
        }
    }
}
