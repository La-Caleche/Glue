package fr.lacaleche.glue.registries;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
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

    public static RenderType.CompositeRenderType createRenderType(String name, RenderPipeline pipeline,
                                                                  RenderType.CompositeState state) {
        return RenderType.create(name, 1536, false, true, pipeline, state);
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

    public List<RenderPipeline> getPipelines() {
        return List.copyOf(this.pipelines);
    }
}
