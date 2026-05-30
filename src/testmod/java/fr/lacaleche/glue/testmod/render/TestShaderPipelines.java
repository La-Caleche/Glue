package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Cyclable shader pipeline registry for the Shader Test Block.
 *
 * <p>Discovers all pipelines from the unified {@link GlueClientRegistries#PIPELINES}
 * registry — both Java-registered ({@code TestShaders.HOLOGRAM}) and
 * JSON-loaded ({@code glue/pipelines/*.json}) appear automatically.</p>
 */
@Environment(EnvType.CLIENT)
public final class TestShaderPipelines {

    private TestShaderPipelines() {
    }

    /** Snapshot of all currently available pipelines (Java + JSON) for this mod. */
    private static List<Entry> resolve() {
        List<Entry> entries = new ArrayList<>();

        for (Map.Entry<ResourceLocation, GluePipeline> e : GlueClientRegistries.PIPELINES.getAll().entrySet()) {
            ResourceLocation id = e.getKey();
            if (TestmodClient.MOD_ID.equals(id.getNamespace())) {
                entries.add(new Entry(id.getPath(), e.getValue()));
            }
        }
        return entries;
    }

    /** Returns the pipeline at the given cycle index. */
    public static GluePipeline get(int index) {
        List<Entry> entries = resolve();
        return entries.get(index % entries.size()).pipeline;
    }

    /** Total number of available pipelines. */
    public static int count() {
        return resolve().size();
    }

    /** Human-readable name of the pipeline at the given cycle index. */
    public static String nameOf(int index) {
        List<Entry> entries = resolve();
        return entries.get(index % entries.size()).name;
    }

    private record Entry(String name, GluePipeline pipeline) {
    }
}
