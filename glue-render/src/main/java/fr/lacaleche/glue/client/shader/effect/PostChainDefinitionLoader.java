package fr.lacaleche.glue.client.shader.effect;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
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
 * Loads {@code glue/post_chains/<name>.json} and pushes baked
 * {@link PostShaderHandle}s into {@link GlueClientRegistries#POST_CHAINS}.
 * Must run before {@link TimedEffectDefinitionLoader}.
 */
@Environment(EnvType.CLIENT)
public class PostChainDefinitionLoader extends SimpleJsonResourceReloadListener<PostChainDefinition>
        implements IdentifiableResourceReloadListener {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final ResourceLocation FABRIC_ID =
            ResourceLocation.fromNamespaceAndPath("glue", "post_chain_loader");

    public PostChainDefinitionLoader() {
        super(PostChainDefinition.CODEC, FileToIdConverter.json("glue/post_chains"));
    }

    @Override
    protected void apply(Map<ResourceLocation, PostChainDefinition> definitions,
                         ResourceManager resourceManager,
                         ProfilerFiller profiler) {
        Map<ResourceLocation, PostShaderHandle> built = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, PostChainDefinition> entry : definitions.entrySet()) {
            ResourceLocation id = entry.getKey();
            try {
                built.put(id, entry.getValue().bake(id));
            } catch (Exception e) {
                LOGGER.error("[Glue] Failed to bake post chain {}", id, e);
            }
        }

        GlueClientRegistries.POST_CHAINS.reload(built);

        if (!built.isEmpty()) {
            LOGGER.info("[Glue] Post chain loader: {} handle(s) loaded", built.size());
        }
    }

    @Override
    public ResourceLocation getFabricId() {
        return FABRIC_ID;
    }
}
