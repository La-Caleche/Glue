package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;

public class ItemGroupsRegistry {

  private final String modId;

  public ItemGroupsRegistry(String modId) {
    this.modId = modId;
  }

  private ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(this.modId, path);
  }

  public CreativeModeTab register(String category, CreativeModeTab.Builder builder) {
    return Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, this.id(category), builder.build());
  }
}
