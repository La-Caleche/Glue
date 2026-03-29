package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import org.jetbrains.annotations.Nullable;

/**
 * A test block that demonstrates custom shader rendering capabilities.
 * When placed in the world, it:
 * - Renders an animated gradient quad above it (world shader demo)
 * - Applies a post-processing grayscale/blur effect when the player is nearby
 */
public class TestShaderBlock extends BaseEntityBlock {

    public static final MapCodec<TestShaderBlock> CODEC = simpleCodec(TestShaderBlock::new);

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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestShaderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                   BlockEntityType<T> type) {
        if (level.isClientSide()) {
            return createTickerHelper(type, TestBlockEntities.SHADER_BLOCK_ENTITY, TestShaderBlockEntity::tick);
        }
        return null;
    }
}
