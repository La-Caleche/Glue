package fr.lacaleche.glue.testmod.blocks.demo;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Minimal shared block entity that does nothing but count client ticks.
 *
 * <p>Several demo blocks (outline, spinning, additive sprite) need only an
 * animation clock for their renderers — no persistent state. Rather than
 * duplicate an identical class per block, they all use this one, each
 * registered under its own {@link BlockEntityType} (see
 * {@link fr.lacaleche.glue.testmod.registries.TestBlockEntities}).</p>
 *
 * <p>Blocks that need real state (e.g. the shader block's cycling index) keep
 * their own dedicated block entity instead — see
 * {@link TestShaderBlockEntity}.</p>
 */
public class TickingBlockEntity extends BlockEntity {

    private int ticks = 0;

    public TickingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Ticker entry point — wire up via {@code createTickerHelper(..., TickingBlockEntity::tick)}. */
    public static void tick(Level level, BlockPos pos, BlockState state, TickingBlockEntity entity) {
        entity.ticks++;
    }

    /** Number of ticks elapsed since this entity loaded; used to drive renderer animations. */
    public int getTicks() {
        return ticks;
    }
}
