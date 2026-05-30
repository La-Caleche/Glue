package fr.lacaleche.glue.registries;

import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinition;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

@Environment(EnvType.CLIENT)
public class TimedEffectRegistry extends GlueRegistry {

    public TimedEffectRegistry(String modId) {
        super(modId);
    }

    public TimedEffectRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public TimedEffectDefinition register(String name, TimedEffectDefinition definition) {
        return GlueClientRegistries.TIMED_EFFECT_DEFINITIONS.register(this.id(name), definition);
    }
}
