package fr.lacaleche.glue.registries;

import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;

public class ParticlesRegistry {

  private final String modId;

  public ParticlesRegistry(String modId) {
    this.modId = modId;
  }

  private ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(this.modId, path);
  }

  public SimpleParticleType register(String name) {
    SimpleParticleType simpleParticleType = FabricParticleTypes.simple();
    Registry.register(BuiltInRegistries.PARTICLE_TYPE, this.id(name), simpleParticleType);
    return simpleParticleType;
  }
}
