package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Function;

public class BlockEntitiesRegistry extends GlueRegistry {

    public BlockEntitiesRegistry(String modId) {
        super(modId);
    }

    public BlockEntitiesRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public <T extends BlockEntityType<?>> T register(String path, T blockEntityType) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, this.id(path), blockEntityType);
    }
}
