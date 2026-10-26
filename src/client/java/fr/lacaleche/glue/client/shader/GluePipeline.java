package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.shader.internal.RenderPassBinders;
import fr.lacaleche.glue.client.shader.internal.RuntimePipelines;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final boolean runtime;

    /** RenderTypes from {@link #createRenderType}, each with a vertex buffer kept across wraps once drawn. */
    private final Set<RenderType> ownedTypes = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Map<RenderType, ByteBufferBuilder> ownedBuffers = new IdentityHashMap<>();
    private boolean released;

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

    private GluePipeline(String name, RenderPipeline pipeline, boolean additive, PipelineCategory category,
                         boolean runtime) {
        this.name = name;
        this.pipeline = pipeline;
        this.additive = additive;
        this.category = category;
        this.runtime = runtime;
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

    /**
     * Creates a {@link Builder} for a pipeline whose GLSL is text in memory rather than resources, finished
     * with {@link Builder#buildRuntime}. Same defaults as {@link #builder}.
     */
    public static Builder runtimeBuilder(ResourceLocation location) {
        return new Builder(location, null, null);
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

    public boolean writesDepth() {
        return pipeline.isWriteDepth();
    }

    public PipelineCategory getCategory() {
        return category;
    }

    /** Whether this pipeline came from {@link Builder#buildRuntime} and must be {@linkplain #release() released}. */
    public boolean isRuntime() {
        return runtime;
    }

    /**
     * Deletes a runtime pipeline's program and shaders and the vertex buffers of its
     * {@linkplain #createRenderType owned RenderTypes}. Call it on the render thread, outside any wrap,
     * once nothing draws through it. A second call does nothing.
     *
     * @throws IllegalStateException for a pipeline built from resources, which the resource reload owns
     */
    public void release() {
        if (!runtime)
            throw new IllegalStateException("Pipeline '" + name + "' is built from resources and cannot be released");
        if (released) return;

        released = true;
        for (RenderType type : List.copyOf(ownedTypes))
            releaseRenderType(type);
        RuntimePipelines.release(this);
    }

    /**
     * A {@link RenderType} of this pipeline that the caller owns: outside the LRU of {@link #renderType},
     * kept by {@link ShadedBufferSource} instead of swapped, drawn from a vertex buffer the pipeline keeps
     * across wraps. Its composite state is the category default for {@code texture}. On a pipeline built
     * from resources nothing releases it for the caller: free it with {@link #releaseRenderType}.
     *
     * @param stateKey names the type in debug output
     * @param binder   binds the type's own uniforms and samplers on each draw, after vanilla's samplers; may be null
     */
    public RenderType createRenderType(ResourceLocation texture, String stateKey, @Nullable RenderPassBinder binder) {
        RenderType type = RenderType.create("glue_" + name + "_" + stateKey,
                GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, defaultState(texture, category == PipelineCategory.ENTITY));
        ownedTypes.add(type);
        if (binder != null)
            RenderPassBinders.register(type, binder);
        return type;
    }

    /** Frees what {@link #createRenderType} made; the type must not be drawn again. */
    public void releaseRenderType(RenderType type) {
        if (!ownedTypes.remove(type)) return;
        RenderPassBinders.unregister(type);
        ByteBufferBuilder buffer = ownedBuffers.remove(type);
        if (buffer != null)
            buffer.close();
    }

    boolean ownsRenderType(RenderType type) {
        return ownedTypes.contains(type);
    }

    ByteBufferBuilder ownedBuffer(RenderType type) {
        return ownedBuffers.computeIfAbsent(type, t -> new ByteBufferBuilder(t.bufferSize()));
    }

    /** A batch that failed may leave unbuilt vertices behind; the next draw starts from a fresh buffer. */
    void dropOwnedBuffer(RenderType type) {
        ByteBufferBuilder buffer = ownedBuffers.remove(type);
        if (buffer != null)
            buffer.close();
    }

    /** The state of the category types: the texture and the lightmap, and the overlay for entities. */
    private static RenderType.CompositeState defaultState(ResourceLocation texture, boolean overlay) {
        RenderType.CompositeState.CompositeStateBuilder state = RenderType.CompositeState.builder()
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false))
                .setLightmapState(RenderStateShard.LIGHTMAP);
        if (overlay)
            state.setOverlayState(RenderStateShard.OVERLAY);
        return state.createCompositeState(false);
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
        return renderType(texture, stateKey, true, stateFactory);
    }

    /**
     * Variant of {@link #renderType(ResourceLocation, String, Function)} with control over
     * quad sorting.
     *
     * <p>{@code sortOnUpload} makes {@code BufferSource.endBatch} re-sort every quad
     * back-to-front from the projection origin. That is what translucency wants on the
     * camera path, but it silently OVERRIDES any submission order the caller arranged.
     * Pass {@code false} when the pass depends on its own draw order &mdash; e.g. the
     * shadow tint bake, which draws panes front-to-back from the light so the depth test
     * keeps only the nearest one per texel.</p>
     */
    public RenderType renderType(ResourceLocation texture, String stateKey, boolean sortOnUpload,
                                 Function<ResourceLocation, RenderType.CompositeState> stateFactory) {
        String cacheKey = "custom#" + stateKey + "#" + sortOnUpload + "#" + texture;
        return renderTypeCache.computeIfAbsent(cacheKey, k ->
                RenderType.create(
                        "glue_" + name + "_" + stateKey,
                        GlueConstants.DEFAULT_BUFFER_SIZE,
                        false, sortOnUpload, pipeline,
                        stateFactory.apply(texture)));
    }

    /**
     * Entity-category {@link RenderType} with lightmap and overlay state.
     */
    public RenderType entityType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("entity#" + texture, k -> RenderType.create("glue_" + name + "_entity",
                GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, defaultState(texture, true)));
    }

    /**
     * Block-category {@link RenderType} with lightmap state.
     */
    public RenderType blockType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("block#" + texture, k -> RenderType.create("glue_" + name + "_block",
                GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, defaultState(texture, false)));
    }

    /**
     * Particle-category {@link RenderType} with lightmap state.
     */
    public RenderType particleType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent("particle#" + texture, k -> RenderType.create("glue_" + name + "_particle",
                GlueConstants.DEFAULT_BUFFER_SIZE, false, true, pipeline, defaultState(texture, false)));
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
        private final @Nullable ResourceLocation vertShader;
        private final @Nullable ResourceLocation fragShader;

        private RenderPipeline.Snippet snippet = RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET;
        private VertexFormat vertexFormat = DefaultVertexFormat.NEW_ENTITY;
        private VertexFormat.Mode vertexMode = VertexFormat.Mode.QUADS;
        private @Nullable BlendFunction blendFunction = BlendFunction.TRANSLUCENT;
        private boolean alphaCutoutEnabled = true;
        private float alphaCutout = 0.1f;
        private boolean cull = false;
        private boolean colorWrite = true;
        private boolean depthWrite = true;
        private @Nullable DepthTestFunction depthTest = null;
        private float depthBiasScaleFactor = 0.0f;
        private float depthBiasConstant = 0.0f;
        private String irisProgram = "ENTITIES_TRANSLUCENT";
        private PipelineCategory category = PipelineCategory.ENTITY;
        private List<String> samplers = new ArrayList<>(List.of("Sampler0", "Sampler1", "Sampler2"));
        private final Map<String, UniformType> uniforms = new LinkedHashMap<>();

        private Builder(ResourceLocation location, @Nullable ResourceLocation vertShader,
                        @Nullable ResourceLocation fragShader) {
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
         * Whether fragments write colour. Set {@code false} for a depth-only pass
         * (shadow maps, depth pre-passes).
         */
        public Builder colorWrite(boolean colorWrite) {
            this.colorWrite = colorWrite;
            return this;
        }

        /**
         * Whether fragments write depth. Set {@code false} to draw against an existing
         * depth buffer without occluding anything behind you.
         */
        public Builder depthWrite(boolean depthWrite) {
            this.depthWrite = depthWrite;
            return this;
        }

        /**
         * Depth comparison function. Left at the pipeline default ({@code LEQUAL}) unless set.
         */
        public Builder depthTest(DepthTestFunction depthTest) {
            this.depthTest = depthTest;
            return this;
        }

        /**
         * Polygon depth bias (glPolygonOffset). Negative values pull fragments toward the camera,
         * so a coplanar re-render passes a {@code LEQUAL} test against the existing depth without
         * z-fighting -- the decal technique. Both default to 0 (no bias).
         */
        public Builder depthBias(float scaleFactor, float constant) {
            this.depthBiasScaleFactor = scaleFactor;
            this.depthBiasConstant = constant;
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

        /**
         * Declares a uniform the shaders read beyond the snippet's, bound per draw by a
         * {@link RenderPassBinder} (see {@link #createRenderType}).
         *
         * @throws IllegalArgumentException for a texel buffer, which only uniform blocks replace here
         */
        public Builder uniform(String name, UniformType type) {
            if (type == UniformType.TEXEL_BUFFER)
                throw new IllegalArgumentException("Uniform '" + name + "': a texel buffer needs a texture format the builder does not take");
            this.uniforms.put(name, type);
            return this;
        }

        public GluePipeline build() {
            if (vertShader == null || fragShader == null)
                throw new IllegalStateException("A runtime builder is finished with buildRuntime(ShaderSources)");
            RenderPipeline pipeline = RenderPipelines.register(pipelineBuilder(location.getPath(), vertShader, fragShader).build());
            RenderCompat.assignIrisProgram(pipeline, irisProgram);

            return new GluePipeline(location.getPath(), pipeline, isAdditiveBlend(blendFunction), category, false);
        }

        /**
         * Compiles {@code sources} now, on the render thread, into a pipeline no resource reload knows of:
         * Glue compiles it again after each reload, and the caller {@linkplain GluePipeline#release() releases}
         * it once done. Nothing stays allocated when the sources fail to compile or link.
         *
         * @throws IllegalStateException on a builder from {@link GluePipeline#builder}
         */
        public RuntimePipelineResult buildRuntime(ShaderSources sources) {
            if (vertShader != null || fragShader != null)
                throw new IllegalStateException("A builder with shader resources is finished with build()");
            ResourceLocation shaderId = RuntimePipelines.nextShaderId(location);
            RenderPipeline pipeline = pipelineBuilder(shaderId.getPath(), shaderId, shaderId).build();
            String log = RuntimePipelines.compile(pipeline, sources);
            if (log != null)
                return new RuntimePipelineResult.Failed(log);
            RenderCompat.assignIrisProgram(pipeline, irisProgram);

            GluePipeline built = new GluePipeline(location.getPath(), pipeline, isAdditiveBlend(blendFunction), category, true);
            RuntimePipelines.register(built, sources);
            return new RuntimePipelineResult.Compiled(built);
        }

        private RenderPipeline.Builder pipelineBuilder(String path, ResourceLocation vertex, ResourceLocation fragment) {
            RenderPipeline.Builder pb = RenderPipeline.builder(snippet)
                    .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "pipeline/" + path))
                    .withVertexShader(vertex)
                    .withFragmentShader(fragment)
                    .withCull(cull)
                    .withColorWrite(colorWrite)
                    .withDepthWrite(depthWrite)
                    .withVertexFormat(vertexFormat, vertexMode);

            if (depthTest != null) {
                pb.withDepthTestFunction(depthTest);
            }

            if (depthBiasScaleFactor != 0.0f || depthBiasConstant != 0.0f) {
                pb.withDepthBias(depthBiasScaleFactor, depthBiasConstant);
            }

            if (alphaCutoutEnabled) {
                pb.withShaderDefine("ALPHA_CUTOUT", alphaCutout);
            }

            for (String sampler : samplers) {
                pb.withSampler(sampler);
            }

            for (Map.Entry<String, UniformType> uniform : uniforms.entrySet()) {
                pb.withUniform(uniform.getKey(), uniform.getValue());
            }

            if (blendFunction != null) {
                pb.withBlend(blendFunction);
            } else {
                pb.withoutBlend();
            }
            return pb;
        }
    }
}
