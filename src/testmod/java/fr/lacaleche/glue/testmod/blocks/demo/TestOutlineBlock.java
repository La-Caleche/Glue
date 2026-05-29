package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.block.IHaveBigOutline;
import fr.lacaleche.glue.shaper.GlueVoxelShape;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Test block for the custom outline rendering system.
 *
 * <p>Validates {@link GlueBlock}, {@link GlueVoxelShape}, and the
 * data-driven outline renderer loaded from {@code glue/outlines/example.json}.</p>
 */
public class TestOutlineBlock extends BaseEntityBlock implements GlueBlock, IHaveBigOutline {

    public static final MapCodec<TestOutlineBlock> CODEC = simpleCodec(TestOutlineBlock::new);

    protected static final VoxelShape SHAPE = Shapes.block();
    protected static final VoxelShape OUTLINE_SHAPE = new GlueVoxelShape(Block.box(3, 0, 3, 13, 16, 13));

    public TestOutlineBlock(Properties settings) {
        super(settings);
    }

    @Override
    public MapCodec<? extends TestOutlineBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestOutlineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, TestBlockEntities.OUTLINE_BLOCK_ENTITY, TestOutlineBlockEntity::tick);
        }
        return null;
    }

    @Override
    public ResourceLocation getOutlineRenderer() {
        return TestmodClient.id("example");
    }
}
