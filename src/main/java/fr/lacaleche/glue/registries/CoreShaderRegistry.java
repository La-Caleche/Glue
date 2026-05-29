package fr.lacaleche.glue.registries;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class CoreShaderRegistry extends GlueRegistry {

    private final List<RenderPipeline> pipelines = new ArrayList<>();

    public CoreShaderRegistry(String modId) {
        super(modId);
    }

    public CoreShaderRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }


    public RenderPipeline register(String name, RenderPipeline.Snippet snippet,
                                   Consumer<RenderPipeline.Builder> customizer) {
        return register(name, snippet, customizer, null);
    }

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
     * Registers a {@link GluePipeline} backed by the fluent {@link GluePipeline.Builder}
     * and inserts it into {@link GlueClientRegistries#PIPELINES} so it can be resolved
     * by id from anywhere (other JSON files, data components, registry iteration, tags).
     *
     * <p>Use this overload when you want a wrapped pipeline ({@link GluePipeline#wrap()}
     * for capture-blit) and id-based lookup. Use the raw
     * {@link #register(String, Consumer) register} overloads when you only need a
     * plain {@link RenderPipeline} (e.g. GUI shaders without compositing needs).</p>
     */
    public GluePipeline registerPipeline(String name,
                                         ResourceLocation vertexShader,
                                         ResourceLocation fragmentShader,
                                         Consumer<GluePipeline.Builder> customizer) {
        GluePipeline.Builder builder = GluePipeline.builder(this.id(name), vertexShader, fragmentShader);
        customizer.accept(builder);
        GluePipeline pipeline = builder.build();
        return Registry.register(GlueClientRegistries.PIPELINES, this.id(name), pipeline);
    }

    public List<RenderPipeline> getPipelines() {
        return List.copyOf(this.pipelines);
    }
}
