package fr.lacaleche.glue.internal;

import fr.lacaleche.glue.Glue;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class GlueRegistries {

    private static <T> DefaultedRegistry<T> registerDefaulted(
            String name, ResourceLocation defaultId
    ) {
        final ResourceKey<Registry<T>> resourceKey = ResourceKey.createRegistryKey(Glue.id(name));
        return FabricRegistryBuilder.createDefaulted(resourceKey, defaultId).buildAndRegister();
    }

    public static void bootstrap() {

    }
}
