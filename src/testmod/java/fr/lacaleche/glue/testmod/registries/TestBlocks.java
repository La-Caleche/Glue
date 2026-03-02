package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlocksRegistry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.debug.TestDebugBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlock;

public class TestBlocks {

    public static final BlocksRegistry REGISTRY = new BlocksRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final Block TEST_DEBUG_BLOCK = REGISTRY.register("test_debug", TestDebugBlock::new,
            BlockBehaviour.Properties.of().noOcclusion().mapColor(MapColor.COLOR_RED).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().strength(1.5F, 6.0F));

    public static final Block TEST_SPINNING_BLOCK = REGISTRY.register("test_spinning", TestSpinningBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).noOcclusion().isViewBlocking(Blocks::never));

    public static void registerBlocks() {
        TestmodClient.LOGGER.info("Registering blocks");
    }

}
