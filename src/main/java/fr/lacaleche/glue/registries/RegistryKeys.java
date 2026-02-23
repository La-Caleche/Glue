package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class RegistryKeys {

  private final String modId;

  public RegistryKeys(String modId) {
    this.modId = modId;
  }

  private ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(this.modId, path);
  }

  public <T> ResourceKey<Registry<T>> of(String id) {
    return ResourceKey.createRegistryKey(this.id(id));
  }
}
