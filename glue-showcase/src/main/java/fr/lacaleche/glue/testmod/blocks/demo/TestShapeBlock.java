package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Test block whose outline and collision are generated from its models by
 * {@code ShowcaseBlockShapes}: it declares no shape in Java.
 *
 * <p>Right-click to cycle through four models. The block places facing the player, and each facing
 * uses the blockstate's y rotation, which the generated shapes follow.</p>
 */
public class TestShapeBlock extends HorizontalDirectionalBlock implements GlueBlock {

    public static final MapCodec<TestShapeBlock> CODEC = simpleCodec(TestShapeBlock::new);
    public static final int MAX_MODE = 3;
    public static final IntegerProperty MODE = IntegerProperty.create("mode", 0, MAX_MODE);

    private static final String[] MODE_NAMES = {
            "L-Bracket", "Arrow", "Log", "I-Beam"
    };

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
}
