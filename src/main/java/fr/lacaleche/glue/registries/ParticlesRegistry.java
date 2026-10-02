package fr.lacaleche.glue.registries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class ParticlesRegistry extends GlueRegistry {

    public ParticlesRegistry(String modId) {
        super(modId);
    }

    public ParticlesRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public SimpleParticleType register(String name) {
        SimpleParticleType simpleParticleType = FabricParticleTypes.simple();
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, this.id(name), simpleParticleType);
        return simpleParticleType;
    }
}
