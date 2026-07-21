package fr.lacaleche.glue.registries;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

public class BlocksRegistry extends GlueRegistry {

    public BlocksRegistry(String modId) {
        super(modId);
    }

    public BlocksRegistry(String modId, Function<String, ResourceLocation> idFunction) {
        super(modId, idFunction);
    }

    public Block register(
            String name, Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties settings
    ) {
        final ResourceLocation identifier = this.id(name);
        final ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, identifier);
        Block block = factory.apply(settings.setId(blockKey));
        return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
    }
}
