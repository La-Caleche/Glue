package fr.lacaleche.glue.testmod.blocks.demo;

import fr.lacaleche.glue.block.GlueBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.mojang.serialization.MapCodec;

public class TestSpinningBlock extends BaseEntityBlock implements GlueBlock {

    public static final MapCodec<TestSpinningBlock> CODEC = simpleCodec(TestSpinningBlock::new);

    public TestSpinningBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestSpinningBlockEntity(pos, state);
    }
}
