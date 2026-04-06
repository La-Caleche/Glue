package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.block.IHaveBigOutline;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * A test block that demonstrates custom shader rendering capabilities.
 * Right-click to cycle through shader effects (hologram, enchanted_glow, frozen, xray, inferno).
 */
public class TestShaderBlock extends BaseEntityBlock implements GlueBlock, IHaveBigOutline {

    public static final MapCodec<TestShaderBlock> CODEC = simpleCodec(TestShaderBlock::new);

    protected static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 4, 0, 16, 22, 16), Block.box(-3, 0, -3, 19, 4, 19)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

    private static final String[] SHADER_NAMES = {
            "Hologram", "Enchanted Glow", "Frozen", "X-Ray", "Inferno"
    };

    public TestShaderBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestShaderBlockEntity(pos, state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getShape(BlockState blockState, BlockGetter blockGetter, BlockPos blockPos, CollisionContext collisionContext) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, TestBlockEntities.SHADER_BLOCK_ENTITY, TestShaderBlockEntity::tick);
        }
        return null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TestShaderBlockEntity entity) {
            entity.cycleShader();
            if (!level.isClientSide()) {
                String name = SHADER_NAMES[entity.getShaderIndex() % SHADER_NAMES.length];
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§b[Glue] §fShader: §e" + name),
                        true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

}
