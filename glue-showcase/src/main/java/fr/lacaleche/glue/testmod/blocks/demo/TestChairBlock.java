package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.block.Rotation16;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * Occamod's chair: placed in sixteen directions. Its one blockstate model is turned by 22.5° per
 * step in the chunk mesh ({@code GlueBlockModels.rotation16}), and its shapes are generated with
 * {@code rotation16}, so its outline and collision follow the chair at every angle.
 */
public class TestChairBlock extends Block implements GlueBlock {

    public static final MapCodec<TestChairBlock> CODEC = simpleCodec(TestChairBlock::new);
    public static final IntegerProperty ROTATION = BlockStateProperties.ROTATION_16;

    public TestChairBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(ROTATION, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ROTATION);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(ROTATION, Rotation16.forPlacement(context));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return Rotation16.rotate(state, ROTATION, rotation);
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return Rotation16.mirror(state, ROTATION, mirror);
    }
}
