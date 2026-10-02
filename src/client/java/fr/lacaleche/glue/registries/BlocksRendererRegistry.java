package fr.lacaleche.glue.registries;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.block.Block;

@Environment(EnvType.CLIENT)
public class BlocksRendererRegistry {

    public void registerCutout(Block... blocks) {
        BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT, blocks);
    }

    public void registerTint(Block block, int color) {
        ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> color, block);
    }

    public void registerTint(Block block, Block reference) {
        ColorProviderRegistry.BLOCK.register((state, view, pos, tintIndex) -> reference.defaultMapColor().col, block);
    }

    public void registerTint(Block block, BlockColor provider) {
        ColorProviderRegistry.BLOCK.register(provider, block);
    }
}
