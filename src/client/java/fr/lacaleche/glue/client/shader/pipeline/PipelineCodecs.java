package fr.lacaleche.glue.client.shader.pipeline;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.serialization.Codec;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.StringRepresentable;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

/**
 * Codec wrappers around Mojang's static-constant types (snippets, vertex
 * formats, blend functions) so they can be referenced by name from JSON.
 *
 * <p>Each helper exposes a {@code CODEC} and a {@code resolve(...)} method that
 * recovers the underlying Mojang object — call {@code resolve} when baking a
 * {@link PipelineDefinition} into a {@link GluePipeline}.</p>
 */
public final class PipelineCodecs {

    public static final Codec<VertexFormat.Mode> VERTEX_FORMAT_MODE_CODEC =
            Codec.STRING.xmap(s -> VertexFormat.Mode.valueOf(s.toUpperCase()), Enum::name);
    public static final Codec<GluePipeline.PipelineCategory> CATEGORY_CODEC =
            Codec.STRING.xmap(s -> GluePipeline.PipelineCategory.valueOf(s.toUpperCase()), e -> e.name().toLowerCase());

    private PipelineCodecs() {
    }

    public enum SnippetKey implements StringRepresentable {
        MATRICES_PROJECTION("matrices_projection", RenderPipelines.MATRICES_PROJECTION_SNIPPET),
        FOG("fog", RenderPipelines.FOG_SNIPPET),
        GLOBALS("globals", RenderPipelines.GLOBALS_SNIPPET),
        MATRICES_FOG("matrices_fog", RenderPipelines.MATRICES_FOG_SNIPPET),
        MATRICES_FOG_LIGHT_DIR("matrices_fog_light_dir", RenderPipelines.MATRICES_FOG_LIGHT_DIR_SNIPPET),
        TERRAIN("terrain", RenderPipelines.TERRAIN_SNIPPET),
        ENTITY("entity", RenderPipelines.ENTITY_SNIPPET),
        ENTITY_EMISSIVE("entity_emissive", RenderPipelines.ENTITY_EMISSIVE_SNIPPET),
        PARTICLE("particle", RenderPipelines.PARTICLE_SNIPPET),
        GUI("gui", RenderPipelines.GUI_SNIPPET),
        GUI_TEXTURED("gui_textured", RenderPipelines.GUI_TEXTURED_SNIPPET);

        public static final Codec<SnippetKey> CODEC = StringRepresentable.fromEnum(SnippetKey::values);

        private final String name;
        private final RenderPipeline.Snippet snippet;

        SnippetKey(String name, RenderPipeline.Snippet snippet) {
            this.name = name;
            this.snippet = snippet;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public RenderPipeline.Snippet resolve() {
            return snippet;
        }
    }

    public enum VertexFormatKey implements StringRepresentable {
        BLOCK("block", DefaultVertexFormat.BLOCK),
        NEW_ENTITY("new_entity", DefaultVertexFormat.NEW_ENTITY),
        PARTICLE("particle", DefaultVertexFormat.PARTICLE),
        POSITION("position", DefaultVertexFormat.POSITION),
        POSITION_COLOR("position_color", DefaultVertexFormat.POSITION_COLOR),
        POSITION_TEX("position_tex", DefaultVertexFormat.POSITION_TEX),
        POSITION_TEX_COLOR("position_tex_color", DefaultVertexFormat.POSITION_TEX_COLOR);

        public static final Codec<VertexFormatKey> CODEC = StringRepresentable.fromEnum(VertexFormatKey::values);

        private final String name;
        private final VertexFormat format;

        VertexFormatKey(String name, VertexFormat format) {
            this.name = name;
            this.format = format;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public VertexFormat resolve() {
            return format;
        }
    }

    /**
     * Closed-name lookup over the static-constant {@link BlendFunction} records on
     * {@code BlendFunction.*}. JSON uses the field name as a string token
     * ({@code "translucent"}, {@code "additive"}, …).
     */
    public enum BlendFunctionKey implements StringRepresentable {
        LIGHTNING("lightning", BlendFunction.LIGHTNING),
        GLINT("glint", BlendFunction.GLINT),
        OVERLAY("overlay", BlendFunction.OVERLAY),
        TRANSLUCENT("translucent", BlendFunction.TRANSLUCENT),
        TRANSLUCENT_PREMULTIPLIED_ALPHA("translucent_premultiplied_alpha", BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA),
        ADDITIVE("additive", BlendFunction.ADDITIVE),
        ENTITY_OUTLINE_BLIT("entity_outline_blit", BlendFunction.ENTITY_OUTLINE_BLIT),
        INVERT("invert", BlendFunction.INVERT);

        public static final Codec<BlendFunctionKey> CODEC = StringRepresentable.fromEnum(BlendFunctionKey::values);

        private static final Map<BlendFunction, BlendFunctionKey> REVERSE =
                Arrays.stream(values()).collect(java.util.stream.Collectors.toMap(BlendFunctionKey::resolve, Function.identity()));

        private final String name;
        private final BlendFunction function;

        BlendFunctionKey(String name, BlendFunction function) {
            this.name = name;
            this.function = function;
        }

        public static BlendFunctionKey from(BlendFunction function) {
            return REVERSE.get(function);
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public BlendFunction resolve() {
            return function;
        }
    }
}
