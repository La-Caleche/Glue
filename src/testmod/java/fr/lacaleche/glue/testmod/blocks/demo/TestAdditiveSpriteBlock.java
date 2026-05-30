package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Demonstration block that renders an additive sprite above it.
 * Place this block in-world to see the particle01 texture rendered
 * with additive blending through Glue's custom pipeline system.
 */
public class TestAdditiveSpriteBlock extends BaseEntityBlock implements GlueBlock {

    public static final MapCodec<TestAdditiveSpriteBlock> CODEC = simpleCodec(TestAdditiveSpriteBlock::new);

    public TestAdditiveSpriteBlock(Properties properties) {
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, TestBlockEntities.ADDITIVE_SPRITE_BLOCK_ENTITY,
                    TickingBlockEntity::tick);
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TickingBlockEntity(TestBlockEntities.ADDITIVE_SPRITE_BLOCK_ENTITY, pos, state);
    }
}
