package fr.lacaleche.glue.client.shader.effect;

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

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code glue/post_effects/<name>.json} and pushes parsed
 * {@link TimedEffectDefinition}s into
 * {@link GlueClientRegistries#TIMED_EFFECT_DEFINITIONS}.
 * Must run after {@link PostChainDefinitionLoader}.
 */
@Environment(EnvType.CLIENT)
public class TimedEffectDefinitionLoader extends SimpleJsonResourceReloadListener<TimedEffectDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "timed_effect_loader");

    private static TimedEffectDefinitionLoader instance;

    public TimedEffectDefinitionLoader() {
        super(TimedEffectDefinition.CODEC, FileToIdConverter.json("glue/post_effects"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, TimedEffectDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        for (Map.Entry<ResourceLocation, TimedEffectDefinition> entry : definitions.entrySet()) {
            if (!GlueClientRegistries.POST_CHAINS.containsKey(entry.getValue().postChain())) {
                LOGGER.warn("[Glue] Timed effect '{}' references unknown post chain '{}'",
                        entry.getKey(), entry.getValue().postChain());
            }
        }

        GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.reload(definitions);

        if (!definitions.isEmpty()) {
            LOGGER.info("[Glue] Timed effect loader: {} definition(s) loaded", definitions.size());
        }
    }

    public TimedEffectDefinition get(ResourceLocation id) {
        return GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.get(id);
    }

    public Map<ResourceLocation, TimedEffectDefinition> getAll() {
        return GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.getAll();
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }

    @Override
    public Collection<ResourceLocation> getFabricDependencies() {
        return List.of(ResourceLocation.fromNamespaceAndPath("glue", "post_chain_loader"));
    }

    public static TimedEffectDefinitionLoader getInstance() {
        return instance;
    }
}
