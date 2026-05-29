package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlocksRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestOutlineBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestShapeBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class TestBlocks {

    public static final BlocksRegistry REGISTRY = new BlocksRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final Block TEST_OUTLINE_BLOCK = REGISTRY.register("test_outline", TestOutlineBlock::new,
            BlockBehaviour.Properties.of().noOcclusion().mapColor(MapColor.COLOR_RED).sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops().strength(1.5F, 6.0F));

    public static final Block TEST_SPINNING_BLOCK = REGISTRY.register("test_spinning", TestSpinningBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).noOcclusion().isViewBlocking(Blocks::never));

    public static final Block TEST_SHADER_BLOCK = REGISTRY.register("test_shader", TestShaderBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_BLOCK).noOcclusion().isViewBlocking(Blocks::never));

    public static final Block TEST_ADDITIVE_SPRITE_BLOCK = REGISTRY.register("test_additive_sprite", TestAdditiveSpriteBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).noOcclusion().isViewBlocking(Blocks::never));

    public static final Block TEST_SHAPE_BLOCK = REGISTRY.register("test_shape", TestShapeBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.STONE).noOcclusion());

    public static void registerBlocks() {
        TestmodClient.LOGGER.info("Registering blocks");
    }

}
