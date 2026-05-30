package fr.lacaleche.glue.client.shader.pipeline;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code glue/pipelines/<name>.json}, bakes each via
 * {@link PipelineDefinition#bake} and pushes them into
 * {@link GlueClientRegistries#PIPELINES}.
 */
@Environment(EnvType.CLIENT)
public class PipelineDefinitionLoader extends SimpleJsonResourceReloadListener<PipelineDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "pipeline_loader");

    private static PipelineDefinitionLoader instance;

    public PipelineDefinitionLoader() {
        super(PipelineDefinition.CODEC, FileToIdConverter.json("glue/pipelines"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, PipelineDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, GluePipeline> built = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, PipelineDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                built.put(id, entry.getValue().bake(id));
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake pipeline {}", id, e);
            }
        }

        GlueClientRegistries.PIPELINES.reload(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Pipeline loader: {} pipeline(s) baked", built.size());
        }
    }

    public GluePipeline get(ResourceLocation id) {
        return GlueClientRegistries.PIPELINES.get(id);
    }

    public Map<ResourceLocation, GluePipeline> getAll() {
        return GlueClientRegistries.PIPELINES.getAll();
    }

    public static PipelineDefinitionLoader getInstance() {
        return instance;
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }
}
