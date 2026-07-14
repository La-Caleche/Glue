package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.shaper.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Test block that showcases {@link VoxelShaper} with 4 distinct shapes.
 *
 * <p>Right-click to cycle through shape variants. The block places facing
 * the player — rotate it to verify all directions render correctly.
 * All shapes use {@link VoxelShaper#forHorizontal} with NORTH as base
 * to stay in sync with blockstate model y-rotation.</p>
 */
public class TestShapeBlock extends HorizontalDirectionalBlock implements GlueBlock {

    public static final MapCodec<TestShapeBlock> CODEC = simpleCodec(TestShapeBlock::new);
    public static final int MAX_MODE = 3;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, MAX_MODE);

    private static final VoxelShape SHAPE_0 = Shapes.join(
            Block.box(2, 0, 0, 14, 4, 16),
            Block.box(2, 4, 10, 14, 14, 16),
            BooleanOp.OR);
    private static final VoxelShaper SHAPER_0 = VoxelShaper.forHorizontal(SHAPE_0, Direction.NORTH);

    private static final VoxelShape SHAPE_1 = Shapes.join(
            Block.box(5, 5, 0, 11, 11, 10),
            Block.box(3, 3, 10, 13, 13, 16),
            BooleanOp.OR);
    private static final VoxelShaper SHAPER_1 = VoxelShaper.forHorizontal(SHAPE_1, Direction.NORTH);

    private static final VoxelShape SHAPE_2 = Shapes.join(
            Block.box(3, 2, 0, 13, 14, 16),
            Block.box(5, 0, 0, 11, 16, 16),
            BooleanOp.OR);
    private static final VoxelShaper SHAPER_2 = VoxelShaper.forHorizontal(SHAPE_2, Direction.NORTH);

    private static final VoxelShape SHAPE_3 = Shapes.join(
            Shapes.join(
                    Block.box(2, 2, 0, 14, 14, 3),
                    Block.box(2, 2, 13, 14, 14, 16),
                    BooleanOp.OR),
            Block.box(5, 5, 3, 11, 11, 13),
            BooleanOp.OR);
    private static final VoxelShaper SHAPER_3 = VoxelShaper.forHorizontal(SHAPE_3, Direction.NORTH);

    private static final String[] MODE_NAMES = {
            "L-Bracket", "Arrow", "Log", "I-Beam"
    };
    private static final VoxelShaper[] SHAPERS = { SHAPER_0, SHAPER_1, SHAPER_2, SHAPER_3 };
    private static final VoxelShape[] BASES = { SHAPE_0, SHAPE_1, SHAPE_2, SHAPE_3 };

    public TestShapeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(MODE, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            int next = (state.getValue(MODE) + 1) % (MAX_MODE + 1);
            level.setBlock(pos, state.setValue(MODE, next), 3);
            player.displayClientMessage(
                    Component.literal("§7[Shaper] §f" + MODE_NAMES[next]), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        int mode = state.getValue(MODE);
        Direction facing = state.getValue(FACING);
        VoxelShape shape = SHAPERS[mode].get(facing);
        return shape != null ? shape : BASES[mode];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return getShape(state, world, pos, context);
    }
}
