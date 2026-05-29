package fr.lacaleche.glue.client.shader.pipeline;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code assets/<modid>/glue/pipelines/<name>.json} files, bakes each via
 * {@link PipelineDefinition#bake} and makes them available via {@link #get(ResourceLocation)}.
 *
 * <p>On the <em>first</em> load, baked pipelines are also inserted into
 * {@link GlueClientRegistries#PIPELINES} for backwards compatibility with code
 * that looks up pipelines from the registry. On subsequent reloads (F3+T),
 * the definitions are re-baked into the internal map — the authoritative source
 * for JSON-defined pipelines — while the registry keeps the stale first-load
 * entry (vanilla {@code MappedRegistry} does not support re-registration).</p>
 *
 * <p>Callers wanting hot-reload support <b>must</b> look up via
 * {@link #get(ResourceLocation)}, not the registry.</p>
 */
@Environment(EnvType.CLIENT)
public class PipelineDefinitionLoader extends SimpleJsonResourceReloadListener<PipelineDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "pipeline_loader");

    /**
     * Singleton instance — set during construction (there is only ever one
     * loader registered in {@link fr.lacaleche.glue.client.GlueClient}).
     */
    private static PipelineDefinitionLoader instance;

    /**
     * The authoritative map of JSON-baked pipelines, rebuilt on every reload.
     */
    private volatile Map<ResourceLocation, GluePipeline> pipelines = Collections.emptyMap();

    public PipelineDefinitionLoader() {
        super(PipelineDefinition.CODEC, FileToIdConverter.json("glue/pipelines"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, PipelineDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, GluePipeline> built = new LinkedHashMap<>();
        int newRegistrations = 0;

        for (Map.Entry<ResourceLocation, PipelineDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            PipelineDefinition def = entry.getValue();

            try {
                GluePipeline pipeline = def.bake(id);
                built.put(id, pipeline);

                // First-time: also insert into the registry for legacy lookups
                if (!GlueClientRegistries.PIPELINES.containsKey(id)) {
                    Registry.register(GlueClientRegistries.PIPELINES, id, pipeline);
                    newRegistrations++;
                }
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake pipeline {}", id, e);
            }
        }

        this.pipelines = Collections.unmodifiableMap(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Pipeline loader: {} pipeline(s) baked ({} new registry entries)",
                    built.size(), newRegistrations);
        }
    }

    /**
     * Returns the hot-reloadable pipeline for the given id, or {@code null}
     * if not defined in JSON.
     */
    public GluePipeline get(ResourceLocation id) {
        return pipelines.get(id);
    }

    /**
     * Returns all JSON-baked pipelines (unmodifiable).
     */
    public Map<ResourceLocation, GluePipeline> getAll() {
        return pipelines;
    }

    /**
     * Returns the singleton loader instance, or {@code null} before
     * registration.
     */
    public static PipelineDefinitionLoader getInstance() {
        return instance;
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }
}
