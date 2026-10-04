package fr.lacaleche.composite;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * One part's own code running as if its block stood alone at the cell's position: while it runs on
 * a thread, that level's {@code getBlockState}, {@code getBlockEntity} and {@code setBlock} at the
 * cell's position read and change the part rather than the cell.
 *
 * <p>Scopes nest, and each belongs to the thread that opened it and is closed by it. Outside any
 * scope the lookups cost one volatile read.</p>
 */
public final class PartScope implements AutoCloseable {

    private static final ThreadLocal<PartScope> CURRENT = new ThreadLocal<>();
    private static final AtomicInteger OPEN = new AtomicInteger();

    private final Level level;
    private final BlockPos pos;
    private BlockState state;
    private @Nullable BlockEntity entity;
    private final @Nullable PartScope outer;
    private boolean changed;

    private PartScope(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity, @Nullable PartScope outer) {
        this.level = level;
        this.pos = pos.immutable();
        this.state = state;
        this.entity = entity;
        this.outer = outer;
    }

    /** The scope redirecting a level's position on this thread, or {@code null} when none does. */
    public static @Nullable PartScope at(Level level, BlockPos pos) {
        if (OPEN.get() == 0) return null;
        PartScope scope = CURRENT.get();
        return scope != null && scope.level == level && scope.pos.equals(pos) ? scope : null;
    }

    /**
     * Stands a part's state and block entity at {@code pos} on this thread until {@link #close}, which
     * the opener calls in a {@code finally} block.
     */
    static PartScope open(Level level, BlockPos pos, BlockState state, @Nullable BlockEntity entity) {
        PartScope scope = new PartScope(level, pos, state, entity, CURRENT.get());
        CURRENT.set(scope);
        OPEN.incrementAndGet();
        return scope;
    }

    /** Gives the position back to the scope this one was opened in, or to the level. */
    @Override
    public void close() {
        OPEN.decrementAndGet();
        if (this.outer == null) CURRENT.remove();
        else CURRENT.set(this.outer);
    }

    public BlockState state() {
        return this.state;
    }

    public @Nullable BlockEntity entity() {
        return this.entity;
    }

    /** Whether the part's code set a new state. */
    boolean changed() {
        return this.changed;
    }

    /**
     * Places a state in the part as a chunk places one in a block, for {@code setBlock}: the old
     * block entity reacts to its block's removal, the old state affects its neighbours as it goes,
     * and the new one is placed, each as {@code flags} allow. {@code setBlock} then updates the
     * neighbours and their shapes as for any block.
     *
     * @return the state the part had, or {@code null} when it already had this one
     */
    public @Nullable BlockState place(BlockState state, int flags) {
        BlockState old = this.state;
        if (state == old) return null;
        boolean changesBlock = !old.is(state.getBlock());
        boolean movedByPiston = (flags & Block.UPDATE_MOVE_BY_PISTON) != 0;
        if (changesBlock && this.entity != null && !this.level.isClientSide()
                && (flags & Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS) == 0) {
            this.entity.preRemoveSideEffects(this.pos, old);
        }
        setState(state);
        if ((changesBlock || state.getBlock() instanceof BaseRailBlock) && this.level instanceof ServerLevel server
                && ((flags & Block.UPDATE_NEIGHBORS) != 0 || movedByPiston)) {
            old.affectNeighborsAfterRemoval(server, this.pos, movedByPiston);
        }
        if (!this.level.isClientSide() && (flags & Block.UPDATE_SKIP_ON_PLACE) == 0) state.onPlace(this.level, this.pos, old, movedByPiston);
        return old;
    }

    /**
     * Sets the part's state alone: the block entity follows a state of the same block, and a new
     * block gets a new block entity, or none.
     */
    @SuppressWarnings("deprecation") // Vanilla's chunks keep a block entity through a state of its block the same way.
    public void setState(BlockState state) {
        if (state == this.state) return;
        if (state.is(this.state.getBlock()) && this.entity != null) {
            this.entity.setBlockState(state);
        } else {
            if (this.entity != null) this.entity.setRemoved();
            this.entity = newEntity(this.level, this.pos, state);
        }
        this.state = state;
        this.changed = true;
    }

    /** A new block entity for a state at a position, in a level, or {@code null} for a block without one. */
    static @Nullable BlockEntity newEntity(@Nullable Level level, BlockPos pos, BlockState state) {
        if (!(state.getBlock() instanceof EntityBlock block)) return null;
        BlockEntity entity = block.newBlockEntity(pos, state);
        if (entity != null && level != null) entity.setLevel(level);
        return entity;
    }
}
