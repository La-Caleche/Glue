package fr.lacaleche.glue.testmod.blocks.demo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The additive-sprite demo block entity: a tick clock plus the sprite's bob and pulse, kept here so
 * the renderer reads its animation from one source of truth.
 */
public final class TestAdditiveSpriteBlockEntity extends TickingBlockEntity {

    public TestAdditiveSpriteBlockEntity(BlockEntityType<TestAdditiveSpriteBlockEntity> type, BlockPos pos,
                                        BlockState state) {
        super(type, pos, state);
    }

    public double spriteCenterY(float partialTick) {
        float time = (getTicks() + partialTick) / 20f;
        return 1.8 + Math.sin(time * 1.5) * 0.1;
    }

    public float pulse(float partialTick) {
        float time = (getTicks() + partialTick) / 20f;
        return 1.0f + 0.15f * (float) Math.sin(time * 2.5);
    }
}
