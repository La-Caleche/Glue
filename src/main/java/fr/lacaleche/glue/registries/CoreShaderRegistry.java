package fr.lacaleche.glue.registries;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
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

    /** Registers a raw {@link RenderPipeline} (not tracked in {@link GlueClientRegistries#PIPELINES}). */
    public RenderPipeline registerRaw(String name, RenderPipeline.Snippet snippet,
                                      Consumer<RenderPipeline.Builder> customizer) {
        return registerRaw(name, snippet, customizer, null);
    }

    /** Registers a raw {@link RenderPipeline} with an optional Iris program assignment. */
    public RenderPipeline registerRaw(String name, RenderPipeline.Snippet snippet,
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

    /** Registers a raw {@link RenderPipeline} without a snippet. */
    public RenderPipeline registerRaw(String name, Consumer<RenderPipeline.Builder> customizer) {
        RenderPipeline.Builder builder = RenderPipeline.builder();
        builder.withLocation(this.id("pipeline/" + name));
        customizer.accept(builder);
        RenderPipeline pipeline = builder.build();
        RenderPipelines.register(pipeline);
        this.pipelines.add(pipeline);
        return pipeline;
    }

    /** Registers a {@link GluePipeline} tracked in {@link GlueClientRegistries#PIPELINES}. */
    public GluePipeline registerPipeline(String name,
                                         ResourceLocation vertexShader,
                                         ResourceLocation fragmentShader,
                                         Consumer<GluePipeline.Builder> customizer) {
        GluePipeline.Builder builder = GluePipeline.builder(this.id(name), vertexShader, fragmentShader);
        customizer.accept(builder);
        GluePipeline pipeline = builder.build();
        return GlueClientRegistries.PIPELINES.register(this.id(name), pipeline);
    }

    public List<RenderPipeline> getPipelines() {
        return List.copyOf(this.pipelines);
    }
}
