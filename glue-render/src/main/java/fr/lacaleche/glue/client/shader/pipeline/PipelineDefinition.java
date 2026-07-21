package fr.lacaleche.glue.client.shader.pipeline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven description of a {@link GluePipeline}, loaded from
 * {@code assets/<modid>/glue/pipelines/<name>.json}.
 *
 * <p>{@link #bake(ResourceLocation)} delegates to {@link GluePipeline.Builder}
 * — JSON is a second front door onto the same builder, not a parallel
 * construction path.</p>
 */
public record PipelineDefinition(
        ResourceLocation vertexShader,
        ResourceLocation fragmentShader,
        PipelineCodecs.SnippetKey snippet,
        PipelineCodecs.VertexFormatKey vertexFormat,
        com.mojang.blaze3d.vertex.VertexFormat.Mode vertexMode,
        Optional<PipelineCodecs.BlendFunctionKey> blend,
        Optional<Float> alphaCutout,
        boolean cull,
        List<String> samplers,
        Optional<String> irisProgram,
        GluePipeline.PipelineCategory category
) {

    private static final List<String> DEFAULT_SAMPLERS = List.of("Sampler0", "Sampler1", "Sampler2");

    public static final Codec<PipelineDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("vertex_shader").forGetter(PipelineDefinition::vertexShader),
            ResourceLocation.CODEC.fieldOf("fragment_shader").forGetter(PipelineDefinition::fragmentShader),
            PipelineCodecs.SnippetKey.CODEC
                    .optionalFieldOf("snippet", PipelineCodecs.SnippetKey.MATRICES_FOG_LIGHT_DIR)
                    .forGetter(PipelineDefinition::snippet),
            PipelineCodecs.VertexFormatKey.CODEC
                    .optionalFieldOf("vertex_format", PipelineCodecs.VertexFormatKey.NEW_ENTITY)
                    .forGetter(PipelineDefinition::vertexFormat),
            PipelineCodecs.VERTEX_FORMAT_MODE_CODEC
                    .optionalFieldOf("vertex_mode", com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS)
                    .forGetter(PipelineDefinition::vertexMode),
            PipelineCodecs.BlendFunctionKey.CODEC
                    .optionalFieldOf("blend")
                    .forGetter(PipelineDefinition::blend),
            Codec.FLOAT.optionalFieldOf("alpha_cutout")
                    .forGetter(PipelineDefinition::alphaCutout),
            Codec.BOOL.optionalFieldOf("cull", false)
                    .forGetter(PipelineDefinition::cull),
            Codec.STRING.listOf()
                    .optionalFieldOf("samplers", DEFAULT_SAMPLERS)
                    .forGetter(PipelineDefinition::samplers),
            Codec.STRING.optionalFieldOf("iris_program")
                    .forGetter(PipelineDefinition::irisProgram),
            PipelineCodecs.CATEGORY_CODEC
                    .optionalFieldOf("category", GluePipeline.PipelineCategory.ENTITY)
                    .forGetter(PipelineDefinition::category)
    ).apply(i, PipelineDefinition::new));

    /**
     * Materializes this definition into a {@link GluePipeline}, registered with
     * the vanilla {@code RenderPipelines} registry under the given id.
     */
    public GluePipeline bake(ResourceLocation id) {
        GluePipeline.Builder b = GluePipeline.builder(id, vertexShader, fragmentShader)
                .snippet(snippet.resolve())
                .vertexFormat(vertexFormat.resolve(), vertexMode)
                .cull(cull)
                .samplers(samplers.toArray(new String[0]))
                .category(category);

        if (blend.isPresent()) {
            b.blend(blend.get().resolve());
        } else {
            b.noBlend();
        }

        if (alphaCutout.isPresent()) {
            b.alphaCutout(alphaCutout.get());
        } else {
            b.noAlphaCutout();
        }

        irisProgram.ifPresent(b::irisProgram);

        return b.build();
    }
}
