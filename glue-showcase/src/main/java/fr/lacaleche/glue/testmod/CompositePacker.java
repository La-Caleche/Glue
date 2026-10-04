package fr.lacaleche.glue.testmod;

import com.mojang.serialization.DataResult;
import fr.lacaleche.composite.BlockPart;
import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositeBlocks;
import fr.lacaleche.composite.CompositeCells;
import fr.lacaleche.composite.CompositePart;
import fr.lacaleche.glue.data.components.TransformationComponent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.TagValueInput;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Packs the blocks of a cuboid into one composite cell, each where it stood relative to the others:
 * the group is moved, turned and scaled about the cuboid's centre as one. Block entities keep their
 * data. Air, fluids and composite cells are left out.
 */
final class CompositePacker {

    /** At most as many positions as {@code /fill} reads. */
    static final int MAX_VOLUME = 32768;

    private CompositePacker() {
    }

    /** The scale that fits a cuboid into one block. */
    static float fitScale(BlockPos from, BlockPos to) {
        BoundingBox box = BoundingBox.fromCorners(from, to);
        return 1f / Math.max(box.getXSpan(), Math.max(box.getYSpan(), box.getZSpan()));
    }

    /**
     * Replaces what stands at {@code cell} with the packed blocks; with {@code move}, removes them
     * from the cuboid once the cell holds them.
     *
     * @param offset where the cuboid's centre goes from the cell's centre, in blocks
     * @param yaw    the group's turn, clockwise seen from above, in degrees
     * @return how many blocks the cell holds, or why nothing changed
     */
    static DataResult<Integer> pack(ServerLevel level, BlockPos from, BlockPos to, BlockPos cell,
                                    Vector3f offset, float yaw, float scale, boolean move) {
        BoundingBox box = BoundingBox.fromCorners(from, to);
        long volume = (long) box.getXSpan() * box.getYSpan() * box.getZSpan();
        if (volume > MAX_VOLUME) return DataResult.error(() -> "At most " + MAX_VOLUME + " positions, not " + volume);

        Vector3f halfSize = new Vector3f(box.getXSpan(), box.getYSpan(), box.getZSpan()).mul(0.5f);
        Quaternionf rotation = new Quaternionf().rotationY((float) Math.toRadians(-yaw));
        List<CompositePart> parts = new ArrayList<>();
        List<@Nullable CompoundTag> data = new ArrayList<>();
        List<BlockPos> sources = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.is(CompositeBlocks.COMPOSITE)) continue;

            // A block's centre, from the cuboid's centre, then moved with the group.
            Vector3f centre = new Vector3f(pos.getX() - box.minX() + 0.5f, pos.getY() - box.minY() + 0.5f,
                    pos.getZ() - box.minZ() + 0.5f).sub(halfSize).mul(scale);
            Vector3f translation = rotation.transform(centre).add(offset);
            parts.add(new BlockPart(state, new TransformationComponent(translation, new Quaternionf(rotation),
                    new Vector3f(scale), new Quaternionf())));
            BlockEntity entity = level.getBlockEntity(pos);
            data.add(entity == null ? null : entity.saveWithoutMetadata(level.registryAccess()));
            sources.add(pos.immutable());
        }
        if (parts.isEmpty()) return DataResult.error(() -> "No block to pack between " + from.toShortString() + " and " + to.toShortString());

        // The cell's own block, when packed, is saved above: it must not spill its items as it is
        // replaced, and gets them back if the cell is refused.
        int packedCell = sources.indexOf(cell);
        if (packedCell >= 0 && level.getBlockEntity(cell) instanceof Clearable packed) packed.clearContent();
        DataResult<List<CompositePart>> result = CompositeCells.set(level, cell, parts);
        if (result.isError()) {
            BlockEntity unchanged = level.getBlockEntity(cell);
            if (packedCell >= 0 && unchanged != null && data.get(packedCell) != null) load(level, unchanged, data.get(packedCell));
            return result.map(List::size);
        }

        restore(level, cell, data);
        if (move) {
            for (BlockPos source : sources) {
                if (source.equals(cell)) continue;
                if (level.getBlockEntity(source) instanceof Clearable moved) moved.clearContent();
                level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return DataResult.success(parts.size());
    }

    /** Loads each packed block entity's data into its part's, or into the plain block a single part became. */
    private static void restore(ServerLevel level, BlockPos cell, List<@Nullable CompoundTag> data) {
        BlockEntity target = level.getBlockEntity(cell);
        for (int i = 0; i < data.size(); i++) {
            CompoundTag tag = data.get(i);
            BlockEntity entity = target instanceof CompositeBlockEntity composite ? composite.entity(i) : target;
            if (tag != null && entity != null) load(level, entity, tag);
        }
        BlockState state = level.getBlockState(cell);
        level.sendBlockUpdated(cell, state, state, Block.UPDATE_CLIENTS);
    }

    private static void load(ServerLevel level, BlockEntity entity, CompoundTag data) {
        entity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), data));
        entity.setChanged();
    }
}
