package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

import java.util.function.Function;

public class ItemGroupsRegistry extends GlueRegistry {

    public ItemGroupsRegistry(String modId) {
        super(modId);
    }

    public ItemGroupsRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public CreativeModeTab register(String category, CreativeModeTab.Builder builder) {
        return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, this.id(category), builder.build());
    }
}
