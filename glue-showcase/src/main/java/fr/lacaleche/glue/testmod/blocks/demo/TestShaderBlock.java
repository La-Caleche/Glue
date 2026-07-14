package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.block.IHaveBigOutline;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import fr.lacaleche.glue.testmod.render.TestShaderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.stream.Stream;

/**
 * Demonstrates cycling a rendered item through every registered {@link GluePipeline}.
 * Right-click cycles the shader (state persisted in {@link TestShaderBlockEntity});
 * also implements {@link IHaveBigOutline} for an oversized selection outline.
 */
public class TestShaderBlock extends BaseEntityBlock implements GlueBlock, IHaveBigOutline {

    public static final MapCodec<TestShaderBlock> CODEC = simpleCodec(TestShaderBlock::new);

    protected static final VoxelShape SHAPE = Stream.of(
            Block.box(0, 4, 0, 16, 22, 16), Block.box(-3, 0, -3, 19, 4, 19)
    ).reduce((v1, v2) -> Shapes.join(v1, v2, BooleanOp.OR)).get();

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
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
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
                String name = TestShaderPipelines.nameOf(entity.getShaderIndex());
                player.displayClientMessage(
                        Component.literal("§b[Glue] §fShader: §e" + name), true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }
}
