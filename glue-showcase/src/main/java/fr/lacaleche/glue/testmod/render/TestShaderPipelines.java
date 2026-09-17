package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.registries.ReloadableRegistry;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

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
 * <p>The list is sorted by id and cached: the block entity persists its cycle position as an
 * index, so that index must mean the same pipeline across runs, and {@link #get} is called once
 * per block per frame. The cache is stamped with {@link ReloadableRegistry#version()}, so a
 * resource reload that changes the JSON layer rebuilds it on the next query.</p>
 */
@Environment(EnvType.CLIENT)
public final class TestShaderPipelines {

    private static List<Entry> cached = List.of();
    private static int cachedVersion = -1;

    private TestShaderPipelines() {
    }

    private static List<Entry> entries() {
        // The version stamp only tracks the JSON layer, which is enough: Java pipelines are all
        // registered during client init, before the first query, and every JSON change (including
        // the initial resource load) bumps version().
        int version = GlueClientRegistries.PIPELINES.version();
        if (cachedVersion == version) return cached;

        List<Entry> entries = new ArrayList<>();
        for (Map.Entry<ResourceLocation, GluePipeline> e : GlueClientRegistries.PIPELINES.getAll().entrySet()) {
            ResourceLocation id = e.getKey();
            if (TestmodClient.MOD_ID.equals(id.getNamespace())) {
                entries.add(new Entry(id.getPath(), e.getValue()));
            }
        }
        entries.sort(Comparator.comparing(Entry::name));
        cached = List.copyOf(entries);
        cachedVersion = version;
        return cached;
    }

    /**
     * Returns the pipeline for the given cycle index, wrapping past either end of the list, or
     * {@code null} when the showcase namespace has no pipelines at all (possible after a reload
     * with a broken resource pack) — callers skip rendering rather than crash.
     */
    @Nullable
    public static GluePipeline get(int index) {
        List<Entry> entries = entries();
        if (entries.isEmpty()) return null;
        return entries.get(Math.floorMod(index, entries.size())).pipeline;
    }

    /** Human-readable name of the pipeline at the given cycle index, or {@code "none"} with no pipelines. */
    public static String nameOf(int index) {
        List<Entry> entries = entries();
        if (entries.isEmpty()) return "none";
        return entries.get(Math.floorMod(index, entries.size())).name;
    }

    private record Entry(String name, GluePipeline pipeline) {
    }
}
