package fr.lacaleche.glue.client.registries;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public class GlueClientRegistries {

    public static final DefaultedRegistry<GlueOutlineRenderer> OUTLINE_RENDERERS = registerDefaulted(
            "outline_renderer", Glue.id("base")
    );

    private static <T> DefaultedRegistry<T> registerDefaulted(
            String name, ResourceLocation defaultId
    ) {
        final ResourceKey<Registry<T>> resourceKey = ResourceKey.createRegistryKey(Glue.id(name));
        return FabricRegistryBuilder.createDefaulted(resourceKey, defaultId).buildAndRegister();
    }

    public static void bootstrap() {

    }
}
