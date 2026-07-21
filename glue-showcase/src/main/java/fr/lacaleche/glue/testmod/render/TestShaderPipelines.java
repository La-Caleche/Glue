package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Cyclable shader pipeline registry for the Shader Test Block.
 *
 * <p>Discovers all pipelines from the unified {@link GlueClientRegistries#PIPELINES}
 * registry — both Java-registered ({@code TestShaders.HOLOGRAM}) and
 * JSON-loaded ({@code glue/pipelines/*.json}) appear automatically.</p>
 *
 * <p>The list is sorted by id and cached: the block entity persists its cycle position as an index, so
 * that index must mean the same pipeline across runs, and {@link #get} is called once per block per
 * frame. {@link #invalidate()} drops the cache when a resource reload may have changed the registry.</p>
 */
@Environment(EnvType.CLIENT)
public final class TestShaderPipelines {

    private static List<Entry> cached;

    private TestShaderPipelines() {
    }

    /** Forgets the cached list; the next query rebuilds it from the registry. */
    public static void invalidate() {
        cached = null;
    }

    private static List<Entry> entries() {
        if (cached != null) return cached;

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, GluePipeline> e : GlueClientRegistries.PIPELINES.getAll().entrySet()) {
            ResourceLocation id = e.getKey();
            if (TestmodClient.MOD_ID.equals(id.getNamespace())) {
                entries.add(new Entry(id.getPath(), e.getValue()));
            }
        }
        entries.sort(Comparator.comparing(Entry::name));
        cached = List.copyOf(entries);
        return cached;
    }

    /** Returns the pipeline at the given cycle index. */
    public static GluePipeline get(int index) {
        List<Entry> entries = entries();
        return entries.get(index % entries.size()).pipeline;
    }

    /** Total number of available pipelines. */
    public static int count() {
        return entries().size();
    }

    /** Human-readable name of the pipeline at the given cycle index. */
    public static String nameOf(int index) {
        List<Entry> entries = entries();
        return entries.get(index % entries.size()).name;
    }

    private record Entry(String name, GluePipeline pipeline) {
    }
}
