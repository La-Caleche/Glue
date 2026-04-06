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

/**
 * Defines a custom shader pipeline backed by user-provided {@code .vsh} and {@code .fsh} files.
 *
 * <p>A GluePipeline wraps MC's {@link RenderPipeline} and provides factory methods
 * to create {@link RenderType}s using the custom shader with any texture.</p>
 *
 * <p>Three vertex format presets are available:</p>
 * <ul>
 *   <li>{@link #entity} — {@code NEW_ENTITY} (items, entities, block entities)</li>
 *   <li>{@link #block} — {@code BLOCK} (terrain, block models)</li>
 *   <li>{@link #particle} — {@code PARTICLE} (particles)</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * // Define once (lazy static):
 * private static final GluePipeline HOLOGRAM = GluePipeline.entity(
 *     ResourceLocation.fromNamespaceAndPath("mymod", "hologram"),
 *     ResourceLocation.fromNamespaceAndPath("mymod", "core/hologram"),
 *     ResourceLocation.fromNamespaceAndPath("mymod", "core/hologram"));
 *
 * // Create a RenderType for a specific texture:
 * RenderType rt = HOLOGRAM.entityType(textureLocation);
 *
 * // Or wrap a MultiBufferSource to shade all draws:
 * MultiBufferSource shaded = new ShadedBufferSource(bufferSource, HOLOGRAM);
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class GluePipeline {

    private final String name;
    private final RenderPipeline pipeline;
    private final Map<ResourceLocation, RenderType> renderTypeCache = new HashMap<>();

    private GluePipeline(String name, RenderPipeline pipeline) {
        this.name = name;
        this.pipeline = pipeline;
    }

    /**
     * Returns the underlying MC {@link RenderPipeline}.
     */
    public RenderPipeline getPipeline() {
        return pipeline;
    }

    /**
     * Returns the pipeline name (used as RenderType name prefix).
     */
    public String getName() {
        return name;
    }

    // ── Factory methods ──────────────────────────────────────────

    /**
     * Creates a pipeline for entity/item/block-entity rendering.
     * Vertex format: {@code NEW_ENTITY} (Position, Color, UV0, UV1, UV2, Normal).
     *
     * @param location   unique pipeline identifier (e.g. {@code mymod:hologram})
     * @param vertShader vertex shader path (e.g. {@code mymod:core/hologram})
     * @param fragShader fragment shader path (e.g. {@code mymod:core/hologram})
     */
    public static GluePipeline entity(ResourceLocation location,
                                      ResourceLocation vertShader,
                                      ResourceLocation fragShader) {
        return entity(location, vertShader, fragShader, BlendFunction.TRANSLUCENT);
    }

    /**
     * Creates an entity pipeline with a specific blend mode.
     */
    public static GluePipeline entity(ResourceLocation location,
                                      ResourceLocation vertShader,
                                      ResourceLocation fragShader,
                                      BlendFunction blendFunction) {
        return entityCustom(location, vertShader, fragShader, blendFunction, "ENTITIES_TRANSLUCENT");
    }

    /**
     * Creates an entity pipeline with full control over blend mode and Iris program.
     *
     * @param blendFunction  blend function, or {@code null} for opaque (no blend)
     * @param irisProgram    Iris program name (e.g. "ENTITIES", "ENTITIES_TRANSLUCENT", "BLOCK_ENTITIES")
     */
    public static GluePipeline entityCustom(ResourceLocation location,
                                            ResourceLocation vertShader,
                                            ResourceLocation fragShader,
                                            BlendFunction blendFunction,
                                            String irisProgram) {
        var builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "pipeline/" + location.getPath()))
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

        RenderPipeline pipeline = RenderPipelines.register(builder.build());
        RenderCompat.assignIrisProgram(pipeline, irisProgram);
        return new GluePipeline(location.getPath(), pipeline);
    }

    /**
     * Creates a pipeline for terrain/block rendering.
     * Vertex format: {@code BLOCK} (Position, Color, UV0, UV2, Normal).
     */
    public static GluePipeline block(ResourceLocation location,
                                     ResourceLocation vertShader,
                                     ResourceLocation fragShader) {
        RenderPipeline pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "pipeline/" + location.getPath()))
                        .withVertexShader(vertShader)
                        .withFragmentShader(fragShader)
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withVertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                        .build()
        );
        // Register with Iris so it treats this as a terrain pipeline (not transparent)
        RenderCompat.assignIrisProgram(pipeline, "TERRAIN");
        return new GluePipeline(location.getPath(), pipeline);
    }

    /**
     * Creates a pipeline for particle rendering.
     * Vertex format: {@code PARTICLE} (Position, UV0, Color, UV2).
     */
    public static GluePipeline particle(ResourceLocation location,
                                        ResourceLocation vertShader,
                                        ResourceLocation fragShader) {
        RenderPipeline pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
                        .withLocation(ResourceLocation.fromNamespaceAndPath(location.getNamespace(), "pipeline/" + location.getPath()))
                        .withVertexShader(vertShader)
                        .withFragmentShader(fragShader)
                        .withSampler("Sampler0")
                        .withSampler("Sampler2")
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                        .build()
        );
        // Register with Iris so it treats this as a particle pipeline (not transparent)
        RenderCompat.assignIrisProgram(pipeline, "PARTICLES");
        return new GluePipeline(location.getPath(), pipeline);
    }

    // ── RenderType factories ─────────────────────────────────────

    /**
     * Creates (or retrieves cached) a RenderType with this pipeline and the given texture.
     * Includes lightmap and overlay state shards for full entity rendering.
     */
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

    /**
     * Creates (or retrieves cached) a RenderType with this pipeline and the given texture.
     * Includes lightmap state shard for terrain rendering.
     */
    public RenderType blockType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, 1536, false, true, pipeline, state);
        });
    }

    /**
     * Creates (or retrieves cached) a RenderType with this pipeline and the given texture.
     * Includes lightmap state shard for particle rendering.
     */
    public RenderType particleType(ResourceLocation texture) {
        return renderTypeCache.computeIfAbsent(texture, tex -> {
            RenderType.CompositeState state = RenderType.CompositeState.builder()
                    .setTextureState(new RenderStateShard.TextureStateShard(tex, false))
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .createCompositeState(false);
            return RenderType.create("glue_" + name, 1536, false, true, pipeline, state);
        });
    }

    /**
     * Wraps the given buffer source so that all rendering through it uses this pipeline.
     *
     * @see ShadedBufferSource
     */
    public ShadedBufferSource wrap(net.minecraft.client.renderer.MultiBufferSource bufferSource) {
        return new ShadedBufferSource(bufferSource, this);
    }
}
