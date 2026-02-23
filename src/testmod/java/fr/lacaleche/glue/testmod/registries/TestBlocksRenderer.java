package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.registries.BlocksRendererRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.block.entity.TestDebugBlockEntityRenderer;
import fr.lacaleche.glue.testmod.render.block.entity.TestSpinningBlockEntityRenderer;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

public class TestBlocksRenderer {

    public static final BlocksRendererRegistry REGISTRY = new BlocksRendererRegistry();

    public static void registerBlocksRenderer() {
        TestmodClient.LOGGER.info("Registering blocks renderer settings");

        BlockEntityRenderers.register(TestBlockEntities.DEBUG_BLOCK_ENTITY, TestDebugBlockEntityRenderer::new);
        BlockEntityRenderers.register(TestBlockEntities.SPINNING_BLOCK_ENTITY, TestSpinningBlockEntityRenderer::new);

        REGISTRY.registerCutout(TestBlocks.TEST_DEBUG_BLOCK, TestBlocks.TEST_SPINNING_BLOCK);
    }

}
