package fr.lacaleche.glue.composite;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block of a composite cell. Its shapes come from its {@link CompositeBlockEntity}, so they vary
 * per position rather than per state; its model draws the parts into the chunk mesh.
 */
public class CompositeBlock extends BaseEntityBlock {

    public static final MapCodec<CompositeBlock> CODEC = simpleCodec(CompositeBlock::new);

    public CompositeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CompositeBlockEntity(CompositeCells.BLOCK_ENTITY, pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.outline() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.collision() : Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
