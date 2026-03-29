package fr.lacaleche.glue.registries;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import fr.lacaleche.glue.compat.RenderCompat;
import org.jetbrains.annotations.Nullable;

/**
 * A GlueRegistry for declaring and registering custom {@link RenderPipeline} objects.
 * <p>
 * In MC 1.21.8, core shaders are represented as {@code RenderPipeline} instances rather than
 * the old {@code ShaderInstance} approach. This registry provides convenience methods for
 * building and registering pipelines with a mod-specific namespace.
 *
 * <pre>{@code
 * public static final CoreShaderRegistry CORE = new CoreShaderRegistry("mymod", MyMod::id);
 *
 * public static final RenderPipeline MY_PIPELINE = CORE.register("my_shader",
 *     RenderPipelines.GUI_SNIPPET,
 *     builder -> builder
 *         .withVertexShader(MyMod.id("core/my_shader"))
 *         .withFragmentShader(MyMod.id("core/my_shader"))
 * );
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class CoreShaderRegistry extends GlueRegistry {

    private final List<RenderPipeline> pipelines = new ArrayList<>();

    public CoreShaderRegistry(String modId) {
        super(modId);
    }

    public CoreShaderRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    /**
     * Registers a new {@link RenderPipeline} with the given name, base snippet, and customizer.
     *
     * @param name       The pipeline name (used for location: {@code modid:pipeline/name})
     * @param snippet    The base snippet to start from (e.g. {@code RenderPipelines.GUI_SNIPPET})
     * @param customizer Additional builder configuration (shaders, samplers, uniforms, etc.)
     * @return The registered {@link RenderPipeline}
     */
    public RenderPipeline register(String name, RenderPipeline.Snippet snippet,
                                   Consumer<RenderPipeline.Builder> customizer) {
        return register(name, snippet, customizer, null);
    }

    /**
     * Registers a new {@link RenderPipeline} with Iris compatibility.
     *
     * @param name        The pipeline name
     * @param snippet     The base snippet
     * @param customizer  Builder configuration
     * @param irisProgram The Iris program category (e.g. "BASIC", "TRANSLUCENT", "BLOCK_TRANSLUCENT").
     *                    If null, no Iris registration is performed.
     * @return The registered {@link RenderPipeline}
     */
    public RenderPipeline register(String name, RenderPipeline.Snippet snippet,
                                   Consumer<RenderPipeline.Builder> customizer,
                                   @Nullable String irisProgram) {
        RenderPipeline.Builder builder = RenderPipeline.builder(snippet);
        builder.withLocation(this.id("pipeline/" + name));
        customizer.accept(builder);
        RenderPipeline pipeline = builder.build();
        RenderPipelines.register(pipeline);
        this.pipelines.add(pipeline);

        if (irisProgram != null) {
            RenderCompat.assignIrisProgram(pipeline, irisProgram);
        }

        return pipeline;
    }

    /**
     * Registers a new {@link RenderPipeline} with the given name and customizer (no base snippet).
     *
     * @param name       The pipeline name
     * @param customizer Builder configuration
     * @return The registered {@link RenderPipeline}
     */
    public RenderPipeline register(String name, Consumer<RenderPipeline.Builder> customizer) {
        RenderPipeline.Builder builder = RenderPipeline.builder();
        builder.withLocation(this.id("pipeline/" + name));
        customizer.accept(builder);
        RenderPipeline pipeline = builder.build();
        RenderPipelines.register(pipeline);
        this.pipelines.add(pipeline);
        return pipeline;
    }

    /**
     * Creates a {@link RenderType} from a registered pipeline.
     *
     * @param name     The render type name
     * @param pipeline The pipeline to use
     * @param state    The composite render state
     * @return The created {@link RenderType.CompositeRenderType}
     */
    public static RenderType.CompositeRenderType createRenderType(String name, RenderPipeline pipeline,
                                                                   RenderType.CompositeState state) {
        return RenderType.create(name, 1536, false, true, pipeline, state);
    }

    /**
     * @return All pipelines registered through this registry
     */
    public List<RenderPipeline> getPipelines() {
        return List.copyOf(this.pipelines);
    }
}
