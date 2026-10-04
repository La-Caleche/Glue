package fr.lacaleche.composite;

import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Composite cells: several blocks sharing one position, each moved, turned and scaled on its own.
 *
 * <p>A cell is the {@code glue:composite} block ({@link CompositeBlocks#COMPOSITE}) holding an ordered list of {@link CompositePart}s.
 * A cell left with one untransformed part becomes that plain block, and a cell left with none
 * becomes air, so cells exist only where they are needed. Edits run on the server, which sends the
 * result to the clients.</p>
 */
public final class CompositeCells {

    public static final int MAX_PARTS = 32;

    /**
     * How far, in blocks, a part may reach past its cell: as far as vanilla looks for collision
     * shapes larger than their block, and as Glue's pick looks for blocks reaching into the cells
     * it crosses.
     */
    public static final double MAX_REACH = 1;

    /** Slack on {@link #MAX_REACH}, so that float rounding does not reject a part that touches it. */
    private static final double BOUNDS_TOLERANCE = 1e-4;

    private CompositeCells() {
    }

    /**
     * The parts at a position: a cell's parts, a plain block as one untransformed part, or nothing
     * for air and replaceable blocks.
     */
    public static List<CompositePart> parts(BlockGetter level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity cell) return cell.parts();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.canBeReplaced()) return List.of();
        return List.of(new CompositePart(state));
    }

    /**
     * Replaces what stands at a position with these parts: air for none, the plain block for one
     * untransformed part, a cell otherwise.
     *
     * <p>The block standing at the position keeps its block entity's data as the first untransformed
     * part of its state, and a cell's part kept as the plain block keeps its own. Block entities of
     * parts that go react as to their block's removal: a container drops its items.</p>
     *
     * @return the parts now at the position, or why they were refused: too many, a part that is
     * itself a cell, or a part reaching more than {@link #MAX_REACH} past the cell
     * @throws IllegalStateException on the client, where cells are not edited
     */
    public static DataResult<List<CompositePart>> set(Level level, BlockPos pos, List<CompositePart> parts) {
        if (level.isClientSide()) throw new IllegalStateException("Composite cells are edited on the server");
        if (parts.size() > MAX_PARTS) {
            return DataResult.error(() -> "A cell holds at most " + MAX_PARTS + " parts, not " + parts.size());
        }
        for (CompositePart part : parts) {
            DataResult<CompositePart> checked = check(part);
            if (checked.isError()) return checked.map(valid -> parts);
        }

        if (parts.isEmpty()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            return DataResult.success(List.of());
        }
        if (parts.size() == 1 && parts.getFirst().isIdentity()) {
            toPlainBlock(level, pos, parts.getFirst());
            return DataResult.success(List.copyOf(parts));
        }

        BlockState current = level.getBlockState(pos);
        BlockEntity plainEntity = current.is(CompositeBlocks.COMPOSITE) ? null : level.getBlockEntity(pos);
        int adopting = plainEntity == null ? -1 : untransformedPart(parts, current);
        CompoundTag adopted = adopting < 0 ? null : plainEntity.saveWithoutMetadata(level.registryAccess());
        if (!current.is(CompositeBlocks.COMPOSITE)) {
            int flags = adopted == null ? Block.UPDATE_ALL : Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS;
            level.setBlock(pos, CompositeBlocks.COMPOSITE.defaultBlockState(), flags);
        }
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity cell)) {
            return DataResult.error(() -> "No composite cell could be placed at " + pos.toShortString());
        }
        cell.setParts(parts);
        BlockEntity adopter = adopted == null ? null : cell.entity(adopting);
        if (adopter != null) load(level, adopter, adopted);
        return DataResult.success(cell.parts());
    }

    /** Adds a part on top of what stands at a position. See {@link #set}. */
    public static DataResult<List<CompositePart>> add(Level level, BlockPos pos, CompositePart part) {
        List<CompositePart> parts = new ArrayList<>(parts(level, pos));
        parts.add(part);
        return set(level, pos, parts);
    }

    /** Removes the part at an index of a position's parts. See {@link #set}. */
    public static DataResult<List<CompositePart>> remove(Level level, BlockPos pos, int index) {
        List<CompositePart> parts = new ArrayList<>(parts(level, pos));
        if (index < 0 || index >= parts.size()) {
            return DataResult.error(() -> "No part " + index + " at " + pos.toShortString() + ", which has " + parts.size());
        }
        parts.remove(index);
        return set(level, pos, parts);
    }

    /**
     * Stands one untransformed part as its plain block. A part kept from a cell keeps its block
     * entity's data; the cell's other parts are removed as {@link CompositeBlockEntity#setParts} removes them.
     */
    private static void toPlainBlock(Level level, BlockPos pos, CompositePart part) {
        CompoundTag kept = null;
        if (level.getBlockEntity(pos) instanceof CompositeBlockEntity cell && cell.parts().stream().anyMatch(candidate -> candidate == part)) {
            cell.setParts(List.of(part));
            BlockEntity entity = cell.entity(0);
            if (entity != null) kept = entity.saveWithoutMetadata(level.registryAccess());
        }
        level.setBlock(pos, part.state(), kept == null ? Block.UPDATE_ALL : Block.UPDATE_ALL | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
        BlockEntity entity = kept == null ? null : level.getBlockEntity(pos);
        if (entity != null) load(level, entity, kept);
    }

    /** The index of the first untransformed part of a state, which the block of that state becomes, or {@code -1}. */
    private static int untransformedPart(List<CompositePart> parts, BlockState state) {
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).state() == state && parts.get(i).isIdentity()) return i;
        }
        return -1;
    }

    private static void load(Level level, BlockEntity entity, CompoundTag data) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), Composite.LOGGER)) {
            entity.loadWithComponents(TagValueInput.create(reporter, level.registryAccess(), data));
        }
        entity.setChanged();
    }

    private static DataResult<CompositePart> check(CompositePart part) {
        BlockState state = part.state();
        if (state.is(CompositeBlocks.COMPOSITE)) return DataResult.error(() -> "A cell cannot hold another cell");
        AABB bounds = part.bounds();
        if (bounds != null && !withinReach(bounds)) {
            return DataResult.error(() -> state.getBlock().getName().getString() + " reaches more than "
                    + MAX_REACH + " block past its cell: " + bounds);
        }
        return DataResult.success(part);
    }

    private static boolean withinReach(AABB bounds) {
        double min = -MAX_REACH - BOUNDS_TOLERANCE;
        double max = 1 + MAX_REACH + BOUNDS_TOLERANCE;
        return bounds.minX >= min && bounds.minY >= min && bounds.minZ >= min
                && bounds.maxX <= max && bounds.maxY <= max && bounds.maxZ <= max;
    }
}
