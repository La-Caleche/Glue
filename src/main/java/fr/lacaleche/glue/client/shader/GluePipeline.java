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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Wraps a {@link RenderPipeline} and provides factory methods for the three
 * standard geometry categories (entity, block, particle).
 *
 * <p>Use the convenience factories for common cases, or {@link #builder} for
 * full control. All factories delegate to {@link Builder} — a single code path
 * for construction.</p>
 *
 * <p>To draw geometry through this pipeline, call {@link #wrap()} to get a
 * {@link ShadedBufferSource}, then pass it as the {@link net.minecraft.client.renderer.MultiBufferSource}
 * to your renderer.</p>
 */
@Environment(EnvType.CLIENT)
public class GluePipeline {

    private static final int MAX_RENDER_TYPE_CACHE = 16;

    private final String name;
    private final RenderPipeline pipeline;
    private final boolean additive;
    private final PipelineCategory category;

    /**
     * LRU cache: prefixed string key → RenderType. Keys are
     * {@code "<flavor>#<texture>"} so the three category factories do not
     * collide on the same texture, and so the custom-state factory can use
     * arbitrary state keys (including characters not valid in a
     * {@link ResourceLocation} path).
     */
    private final Map<String, RenderType> renderTypeCache =
            new LinkedHashMap<>(MAX_RENDER_TYPE_CACHE, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, RenderType> eldest) {
                    return size() > MAX_RENDER_TYPE_CACHE;
                }
            };

    private GluePipeline(String name, RenderPipeline pipeline, boolean additive, PipelineCategory category) {
        this.name = name;
        this.pipeline = pipeline;
        this.additive = additive;
        this.category = category;
    }

    /**
     * Entity pipeline with {@link BlendFunction#TRANSLUCENT} and {@code ENTITIES_TRANSLUCENT} Iris program.
     */
    public static GluePipeline entity(ResourceLocation location,
                                      ResourceLocation vertShader,
                                      ResourceLocation fragShader) {
        return entity(location, vertShader, fragShader, BlendFunction.TRANSLUCENT);
    }

    /**
     * Entity pipeline with a custom blend function and {@code ENTITIES_TRANSLUCENT} Iris program.
     */
    public static GluePipeline entity(ResourceLocation location,
                                      ResourceLocation vertShader,
                                      ResourceLocation fragShader,
                                      BlendFunction blendFunction) {
        return builder(location, vertShader, fragShader)
                .blend(blendFunction)
                .build();
    }

    /**
     * Entity pipeline with a fully custom Iris program name.
     *
     * @deprecated Use
     * {@code builder(loc, vert, frag).blend(blend).irisProgram(program).build()}
     * instead. This method will be removed in a future version.
     */
    @Deprecated(forRemoval = true)
    public static GluePipeline entityCustom(ResourceLocation location,
                                            ResourceLocation vertShader,
                                            ResourceLocation fragShader,
                                            BlendFunction blendFunction,
                                            String irisProgram) {
        return builder(location, vertShader, fragShader)
                .blend(blendFunction)
                .irisProgram(irisProgram)
                .build();
    }

    /**
     * Block pipeline with {@link BlendFunction#TRANSLUCENT} and {@code TERRAIN} Iris program.
     */
    public static GluePipeline block(ResourceLocation location,
                                     ResourceLocation vertShader,
                                     ResourceLocation fragShader) {
        return block(location, vertShader, fragShader, BlendFunction.TRANSLUCENT, "TERRAIN");
    }

    /**
     * Block pipeline with a custom blend function and Iris program.
     */
    public static GluePipeline block(ResourceLocation location,
                                     ResourceLocation vertShader,
                                     ResourceLocation fragShader,
                                     BlendFunction blendFunction,
                                     String irisProgram) {
        return builder(location, vertShader, fragShader)
                .snippet(RenderPipelines.MATRICES_FOG_SNIPPET)
                .vertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                .samplers("Sampler0", "Sampler2")
                .noAlphaCutout()
                .blend(blendFunction)
                .irisProgram(irisProgram)
                .category(PipelineCategory.BLOCK)
                .build();
    }

    /**
     * Particle pipeline with {@link BlendFunction#TRANSLUCENT} and {@code PARTICLES} Iris program.
     */
    public static GluePipeline particle(ResourceLocation location,
                                        ResourceLocation vertShader,
                                        ResourceLocation fragShader) {
        return particle(location, vertShader, fragShader, BlendFunction.TRANSLUCENT, "PARTICLES");
    }

    /**
     * Particle pipeline with a custom blend function and Iris program.
     */
    public static GluePipeline particle(ResourceLocation location,
                                        ResourceLocation vertShader,
                                        ResourceLocation fragShader,
                                        BlendFunction blendFunction,
                                        String irisProgram) {
        return builder(location, vertShader, fragShader)
                .snippet(RenderPipelines.MATRICES_FOG_SNIPPET)
                .vertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                .samplers("Sampler0", "Sampler2")
                .noAlphaCutout()
                .blend(blendFunction)
                .irisProgram(irisProgram)
                .category(PipelineCategory.PARTICLE)
                .build();
    }

    /**
     * Creates a {@link Builder} for full control over every pipeline parameter.
     * Defaults match the entity preset:
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

    private static boolean isAdditiveBlend(@Nullable BlendFunction blend) {
        return blend == BlendFunction.LIGHTNING || blend == BlendFunction.ADDITIVE;
    }

    public RenderPipeline getPipeline() {
        return pipeline;
    }

    public String getName() {
        return name;
    }

    /**
     * Whether this pipeline uses additive blending ({@code LIGHTNING} or {@code ADDITIVE}).
     * Used by {@link ShadedBufferSource} to select the correct blit blend mode.
     */
    public boolean isAdditive() {
        return additive;
    }

    public PipelineCategory getCategory() {
        return category;
    }

    /**
     * Returns the standard {@link RenderType} for this pipeline, dispatched by category.
     * Results are cached per texture in an LRU cache (max {@value MAX_RENDER_TYPE_CACHE} entries).
     */
    public RenderType renderType(ResourceLocation texture) {
        return switch (category) {
            case ENTITY -> entityType(texture);
            case BLOCK -> blockType(texture);
            case PARTICLE -> particleType(texture);
        };
    }

    /**
     * Returns a {@link RenderType} for this pipeline with a fully customized composite state.
     *
     * <p>Use this when you need non-default render state (e.g. no lightmap for fullbright sprites,
     * no overlay) without bypassing the LRU cache. The {@code stateKey} is a stable
     * identifier for the customization variant (e.g. {@code "fullbright"}) and forms part
     * of the cache key.</p>
     *
     * <pre>{@code
     * RenderType rt = pipeline.renderType(SPRITE_TEXTURE, "fullbright", texture ->
     *     RenderType.CompositeState.builder()
     *         .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
     *         .setLightmapState(RenderStateShard.NO_LIGHTMAP)
     *         .createCompositeState(false));
     * }</pre>
     *
     * @param texture      texture to bind
     * @param stateKey     stable string key for this customization variant
     * @param stateFactory function that receives the texture and returns a {@link RenderType.CompositeState}
     */
    public RenderType renderType(ResourceLocation texture, String stateKey,
                                 Function<ResourceLocation, RenderType.CompositeState> stateFactory) {
        String cacheKey = "custom#" + stateKey + "#" + texture;
        return renderTypeCache.computeIfAbsent(cacheKey, k ->
                RenderType.create(
                        "glue_" + name + "_" + stateKey,
                        GlueConstants.DEFAULT_BUFFER_SIZE,
                        false, true, pipeline,
                        stateFactory.apply(texture)));
    }

    /**
     * Entity-category {@link RenderType} with lightmap and overlay state.
     */
    public RenderType entityType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("entity#" + texture, k -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name + "_entity",
                    GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
        });
    }

    /**
     * Block-category {@link RenderType} with lightmap state.
     */
    public RenderType blockType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("block#" + texture, k -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name + "_block",
                    GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
        });
    }

    /**
     * Particle-category {@link RenderType} with lightmap state.
     */
    public RenderType particleType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("particle#" + texture, k -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name + "_particle",
                    GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, state);
        });
    }

    /**
     * Creates a {@link ShadedBufferSource} backed by this pipeline.
     * Use inside a try-with-resources block.
     */
    public ShadedBufferSource wrap() {
        return new ShadedBufferSource(this);
    }

    /**
     * Creates a {@link ShadedBufferSource} that captures to an isolated FBO.
     * Use when post-effects need the captured result before compositing.
     */
    public ShadedBufferSource wrapIsolated() {
        return new ShadedBufferSource(this, true);
    }

    /**
     * The geometry category this pipeline targets.
     * Determines which {@link RenderType} factory is used by {@link #renderType(ResourceLocation)}.
     */
    public enum PipelineCategory {
        ENTITY, BLOCK, PARTICLE
    }

    /**
     * Fluent builder for {@link GluePipeline}.
     * All factory methods delegate here — this is the single construction path.
     */
    public static class Builder {

        private final ResourceLocation location;
        private final ResourceLocation vertShader;
        private final ResourceLocation fragShader;

        private RenderPipeline.Snippet snippet = RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET;
        private VertexFormat vertexFormat = DefaultVertexFormat.NEW_ENTITY;
        private VertexFormat.Mode vertexMode = VertexFormat.Mode.QUADS;
        private @Nullable BlendFunction blendFunction = BlendFunction.TRANSLUCENT;
        private boolean alphaCutoutEnabled = true;
        private float alphaCutout = 0.1f;
        private boolean cull = false;
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

        /**
         * Adds the {@code ALPHA_CUTOUT} shader define with the given threshold.
         */
        public Builder alphaCutout(float cutout) {
            this.alphaCutoutEnabled = true;
            this.alphaCutout = cutout;
            return this;
        }

        /**
         * Omits the {@code ALPHA_CUTOUT} shader define entirely.
         */
        public Builder noAlphaCutout() {
            this.alphaCutoutEnabled = false;
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
         * Replaces the sampler list entirely.
         */
        public Builder samplers(String... samplers) {
            this.samplers = new ArrayList<>(List.of(samplers));
            return this;
        }

        /**
         * Appends a sampler to the existing list.
         */
        public Builder addSampler(String sampler) {
            this.samplers.add(sampler);
            return this;
        }

        public GluePipeline build() {
            RenderPipeline.Builder pb = RenderPipeline.builder(snippet)
                    .withLocation(ResourceLocation.fromNamespaceAndPath(
                            location.getNamespace(), "pipeline/" + location.getPath()))
                    .withVertexShader(vertShader)
                    .withFragmentShader(fragShader)
                    .withCull(cull)
                    .withVertexFormat(vertexFormat, vertexMode);

            if (alphaCutoutEnabled) {
                pb.withShaderDefine("ALPHA_CUTOUT", alphaCutout);
            }

            for (String sampler : samplers) {
                pb.withSampler(sampler);
            }

            if (blendFunction != null) {
                pb.withBlend(blendFunction);
            } else {
                pb.withoutBlend();
            }

            boolean isAdditive = isAdditiveBlend(blendFunction);
            RenderPipeline pipeline = RenderPipelines.register(pb.build());
            RenderCompat.assignIrisProgram(pipeline, irisProgram);

            return new GluePipeline(location.getPath(), pipeline, isAdditive, category);
        }
    }
}
