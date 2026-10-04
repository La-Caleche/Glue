package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.BlocksRegistry;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.blocks.demo.TestOutlineBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestChairBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestShapeBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TestStoveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * Demonstrates Glue's {@link BlocksRegistry}: registers the demo blocks with their block properties.
 * The chair and the stove are ported from Occamod to try generated shapes on real models.
 */
public class TestBlocks {

    public static final BlocksRegistry REGISTRY = new BlocksRegistry(Testmod.MOD_ID, Testmod::id);

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

    public static final Block TEST_CHAIR_BLOCK = REGISTRY.register("test_chair", TestChairBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS).noOcclusion());

    public static final Block TEST_STOVE_BLOCK = REGISTRY.register("test_stove", TestStoveBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.BLAST_FURNACE).noOcclusion()
                    .lightLevel(state -> state.getValue(TestStoveBlock.LIT) ? 13 : 0));

    public static void registerBlocks() {
        Testmod.LOGGER.info("Registering blocks");
    }

}
