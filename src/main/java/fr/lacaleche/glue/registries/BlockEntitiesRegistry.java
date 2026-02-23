package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class BlockEntitiesRegistry {

  private final String modId;

  public BlockEntitiesRegistry(String modId) {
    this.modId = modId;
  }

  private ResourceLocation id(String path) {
    return ResourceLocation.fromNamespaceAndPath(this.modId, path);
  }

  public <T extends BlockEntityType<?>> T register(String path, T blockEntityType) {
    return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.id(path), blockEntityType);
  }
}
