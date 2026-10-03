package fr.lacaleche.glue.composite;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.internal.GlueBlocks;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The block of a composite cell. Its shapes come from its {@link CompositeBlockEntity}, so they vary
 * per position rather than per state; its model draws the parts into the chunk mesh.
 */
public class CompositeBlock extends BaseEntityBlock implements GlueBlock {

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
        return new CompositeBlockEntity(GlueBlocks.COMPOSITE_ENTITY, pos, state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.outline() : Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.collision() : Shapes.empty();
    }

    /** Every part's geometry, so outlines and ray casts follow each part at its angle. */
    @Override
    public @Nullable List<PlacedGeometry> getGeometry(BlockState state, BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell.geometry() : null;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
