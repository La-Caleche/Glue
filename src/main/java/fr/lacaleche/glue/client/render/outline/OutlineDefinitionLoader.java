package fr.lacaleche.glue.client.render.outline;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
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
 * Loads {@code assets/<modid>/glue/outlines/<name>.json} files, bakes each via
 * {@link OutlineDefinition#bake()} and makes them available via {@link #get(ResourceLocation)}.
 *
 * <p>On the <em>first</em> load, baked renderers are also inserted into
 * {@link GlueClientRegistries#OUTLINE_RENDERERS} for backwards compatibility.
 * On subsequent reloads (F3+T), the internal map is rebuilt — callers wanting
 * hot-reload support <b>must</b> look up via {@link #get(ResourceLocation)}.</p>
 */
@Environment(EnvType.CLIENT)
public class OutlineDefinitionLoader extends SimpleJsonResourceReloadListener<OutlineDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/outline");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "outline_loader");

    private static OutlineDefinitionLoader instance;

    private volatile Map<ResourceLocation, GlueOutlineRenderer> renderers = Collections.emptyMap();

    public OutlineDefinitionLoader() {
        super(OutlineDefinition.CODEC, FileToIdConverter.json("glue/outlines"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, OutlineDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, GlueOutlineRenderer> built = new LinkedHashMap<>();
        int newRegistrations = 0;

        for (Map.Entry<ResourceLocation, OutlineDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            OutlineDefinition def = entry.getValue();

            try {
                GlueOutlineRenderer renderer = def.bake();
                built.put(id, renderer);

                if (!GlueClientRegistries.OUTLINE_RENDERERS.containsKey(id)) {
                    Registry.register(GlueClientRegistries.OUTLINE_RENDERERS, id, renderer);
                    newRegistrations++;
                }
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake outline renderer {}", id, e);
            }
        }

        this.renderers = Collections.unmodifiableMap(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Outline loader: {} renderer(s) baked ({} new registry entries)",
                    built.size(), newRegistrations);
        }
    }

    /**
     * Returns the hot-reloadable outline renderer for the given id,
     * or {@code null} if not defined in JSON.
     */
    public GlueOutlineRenderer get(ResourceLocation id) {
        return renderers.get(id);
    }

    /**
     * Returns all JSON-baked outline renderers (unmodifiable).
     */
    public Map<ResourceLocation, GlueOutlineRenderer> getAll() {
        return renderers;
    }

    /**
     * Returns the singleton loader instance, or {@code null} before registration.
     */
    public static OutlineDefinitionLoader getInstance() {
        return instance;
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }
}
