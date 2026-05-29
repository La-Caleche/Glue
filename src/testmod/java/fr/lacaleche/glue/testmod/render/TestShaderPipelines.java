package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.pipeline.PipelineDefinitionLoader;
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
 * <p>{@code hologram} is registered in Java to prove the builder path.
 * All other pipelines are discovered automatically from the
 * {@link PipelineDefinitionLoader} by filtering on the {@code glue-test}
 * namespace — no hardcoded ids needed.</p>
 */
@Environment(EnvType.CLIENT)
public final class TestShaderPipelines {

    /** The single Java-built pipeline, excluded from data-driven discovery. */
    private static final ResourceLocation HOLOGRAM_ID = TestmodClient.id("hologram");
    private static GluePipeline hologramPipeline;

    private TestShaderPipelines() {
    }

    /** Eagerly builds the Java-only pipeline. Call once during mod init. */
    public static void init() {
        hologramPipeline = GluePipeline.entity(
                HOLOGRAM_ID,
                TestmodClient.id("core/entity"),
                TestmodClient.id("core/hologram")
        );
    }

    /** Snapshot of all currently available pipelines (Java + discovered JSON). */
    private static List<Entry> resolve() {
        List<Entry> entries = new ArrayList<>();
        entries.add(new Entry("hologram (Java)", hologramPipeline));

        PipelineDefinitionLoader loader = PipelineDefinitionLoader.getInstance();
        if (loader != null) {
            for (Map.Entry<ResourceLocation, GluePipeline> e : loader.getAll().entrySet()) {
                ResourceLocation id = e.getKey();
                if (TestmodClient.MOD_ID.equals(id.getNamespace()) && !id.equals(HOLOGRAM_ID)) {
                    entries.add(new Entry(id.getPath() + " (JSON)", e.getValue()));
                }
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
