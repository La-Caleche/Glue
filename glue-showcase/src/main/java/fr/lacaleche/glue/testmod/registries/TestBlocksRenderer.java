package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlocksRendererRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.block.entity.TestAdditiveSpriteBlockEntityRenderer;
import fr.lacaleche.glue.testmod.render.block.entity.TestChairBlockEntityRenderer;
import fr.lacaleche.glue.testmod.render.block.entity.TestOutlineBlockEntityRenderer;
import fr.lacaleche.glue.testmod.render.block.entity.TestShaderBlockEntityRenderer;
import fr.lacaleche.glue.testmod.render.block.entity.TestSpinningBlockEntityRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.world.level.block.Blocks;

/**
 * Demonstrates Glue's {@link BlocksRendererRegistry#registerCutout} for assigning
 * the cutout render layer to the demo blocks, and wires each block entity to its
 * {@link net.minecraft.client.renderer.blockentity.BlockEntityRenderer}. The chair's template is
 * tinted, as in Occamod, with the map colour of the wood it stands for.
 */
public class TestBlocksRenderer {

    public static final BlocksRendererRegistry REGISTRY = new BlocksRendererRegistry();

    public static void registerBlocksRenderer() {
        TestmodClient.LOGGER.info("Registering blocks renderer settings");

        BlockEntityRenderers.register(TestBlockEntities.OUTLINE_BLOCK_ENTITY, TestOutlineBlockEntityRenderer::new);
        BlockEntityRenderers.register(TestBlockEntities.SPINNING_BLOCK_ENTITY, TestSpinningBlockEntityRenderer::new);
        BlockEntityRenderers.register(TestBlockEntities.SHADER_BLOCK_ENTITY, TestShaderBlockEntityRenderer::new);
        BlockEntityRenderers.register(TestBlockEntities.ADDITIVE_SPRITE_BLOCK_ENTITY, TestAdditiveSpriteBlockEntityRenderer::new);
        BlockEntityRenderers.register(TestBlockEntities.CHAIR_BLOCK_ENTITY, TestChairBlockEntityRenderer::new);

        ColorProviderRegistry.BLOCK.register((state, level, pos, tintIndex) -> Blocks.OAK_PLANKS.defaultMapColor().col,
                TestBlocks.TEST_CHAIR_BLOCK);

        REGISTRY.registerCutout(TestBlocks.TEST_OUTLINE_BLOCK, TestBlocks.TEST_SPINNING_BLOCK, TestBlocks.TEST_SHADER_BLOCK, TestBlocks.TEST_ADDITIVE_SPRITE_BLOCK, TestBlocks.TEST_SHAPE_BLOCK, TestBlocks.TEST_STOVE_BLOCK);
    }

}
