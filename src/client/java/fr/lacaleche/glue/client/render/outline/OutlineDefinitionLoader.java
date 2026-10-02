package fr.lacaleche.glue.client.render.outline;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
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
 * Loads {@code glue/outlines/<name>.json}, bakes each via
 * {@link OutlineDefinition#bake()} and pushes them into
 * {@link GlueClientRegistries#OUTLINE_RENDERERS}.
 */
@Environment(EnvType.CLIENT)
public class OutlineDefinitionLoader extends SimpleJsonResourceReloadListener<OutlineDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/outline");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "outline_loader");

    private static OutlineDefinitionLoader instance;

    public OutlineDefinitionLoader() {
        super(OutlineDefinition.CODEC, FileToIdConverter.json("glue/outlines"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, OutlineDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, GlueOutlineRenderer> built = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, OutlineDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                built.put(id, entry.getValue().bake());
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake outline renderer {}", id, e);
            }
        }

        GlueClientRegistries.OUTLINE_RENDERERS.reload(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Outline loader: {} renderer(s) baked", built.size());
        }
    }

    public GlueOutlineRenderer get(ResourceLocation id) {
        return GlueClientRegistries.OUTLINE_RENDERERS.get(id);
    }

    public Map<ResourceLocation, GlueOutlineRenderer> getAll() {
        return GlueClientRegistries.OUTLINE_RENDERERS.getAll();
    }

    public static OutlineDefinitionLoader getInstance() {
        return instance;
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }
}
