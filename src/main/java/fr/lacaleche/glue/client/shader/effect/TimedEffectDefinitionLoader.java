package fr.lacaleche.glue.client.shader.effect;

import fr.lacaleche.glue.client.shader.TimedPostEffect;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads {@code assets/<modid>/glue/post_effects/<name>.json} files, bakes each
 * into a {@link TimedPostEffect}, and makes them available via {@link #get(ResourceLocation)}.
 *
 * <p>Unlike pipelines, timed effects are not placed into a global registry
 * because they carry mutable tick state. Each definition bakes a fresh
 * instance on every resource reload.</p>
 *
 * <p>Effects that require custom uniform writers or arbitrary curve lambdas
 * cannot be represented in JSON and must still be created in Java — this
 * loader covers the simple "named-curve + putProgress" case.</p>
 */
@Environment(EnvType.CLIENT)
public class TimedEffectDefinitionLoader extends SimpleJsonResourceReloadListener<TimedEffectDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "timed_effect_loader");

    private static TimedEffectDefinitionLoader instance;

    private volatile Map<ResourceLocation, TimedPostEffect> effects = Collections.emptyMap();

    public TimedEffectDefinitionLoader() {
        super(TimedEffectDefinition.CODEC, FileToIdConverter.json("glue/post_effects"));
        instance = this;
    }

    @Override
    protected void apply(Map<ResourceLocation, TimedEffectDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, TimedPostEffect> built = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, TimedEffectDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            TimedEffectDefinition def = entry.getValue();

            try {
                TimedPostEffect effect = def.bake();
                built.put(id, effect);
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake timed effect {}", id, e);
            }
        }

        this.effects = Collections.unmodifiableMap(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Timed effect loader: {} effect(s) baked", built.size());
        }
    }

    /**
     * Returns a baked effect by its resource id, or {@code null} if not found.
     */
    public TimedPostEffect get(ResourceLocation id) {
        return effects.get(id);
    }

    /**
     * Returns all baked effects (unmodifiable).
     */
    public Map<ResourceLocation, TimedPostEffect> getAll() {
        return effects;
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }

    /**
     * Returns the singleton loader instance, or {@code null} before registration.
     */
    public static TimedEffectDefinitionLoader getInstance() {
        return instance;
    }
}
