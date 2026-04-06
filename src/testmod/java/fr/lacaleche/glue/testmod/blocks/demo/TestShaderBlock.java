package fr.lacaleche.glue.testmod.blocks.demo;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import fr.lacaleche.glue.testmod.registries.TestBlockEntities;
import org.jetbrains.annotations.Nullable;

/**
 * A test block that demonstrates custom shader rendering capabilities.
 * Right-click to cycle through shader effects (hologram, enchanted, frozen, xray, inferno).
 */
public class TestShaderBlock extends BaseEntityBlock {

    public static final MapCodec<TestShaderBlock> CODEC = simpleCodec(TestShaderBlock::new);

    private static final String[] SHADER_NAMES = {
            "ET+Translucent", "ET+NoBlend",
            "E+Translucent", "E+NoBlend",
            "B+Translucent", "B+NoBlend"
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TestShaderBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof TestShaderBlockEntity entity) {
            entity.cycleShader();
            if (!level.isClientSide()) {
                String name = SHADER_NAMES[entity.getShaderIndex()];
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("§b[Glue] §fShader: §e" + name),
                        true);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
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
