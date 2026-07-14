package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

public class RegistryKeys extends GlueRegistry {

    public RegistryKeys(String modId) {
        super(modId);
    }

    public RegistryKeys(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public <T> ResourceKey<Registry<T>> of(String id) {
        return ResourceKey.createRegistryKey(this.id(id));
    }
}
