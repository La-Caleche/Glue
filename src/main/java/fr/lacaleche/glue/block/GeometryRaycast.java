package fr.lacaleche.glue.block;

import fr.lacaleche.glue.shaper.PlacedGeometry;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Ray casts that hit blocks where they are drawn: a {@link GlueBlock} on its geometry, so a ray
 * through the gap between a chair's legs reaches what stands behind, and a block reaching past its
 * cell ({@link IHaveBigOutline}, or geometry that overhangs) from the neighbouring cells too.
 *
 * <p>The player's pick goes through {@link #clip}. Blocks are hit on the context's block shape
 * (the outline, for a pick) or on their geometry, never on their collision.</p>
 */
public final class GeometryRaycast {

    /** Hit points this far past a cell's faces still count as inside it, absorbing rounding. */
    private static final double CELL_EPSILON = 1e-6;

    private GeometryRaycast() {
    }

    /**
     * {@link BlockGetter#clip} for blocks as they are drawn: the nearest hit among the cells the ray
     * crosses, a {@link GlueBlock} with geometry hit on that geometry, and the blocks next to those
     * cells that reach into them.
     */
    public static BlockHitResult clip(BlockGetter level, ClipContext context) {
        return trace(level, context).result();
    }

    /** {@link #clip}, with what each step found, for debugging a pick. */
    public static Trace trace(BlockGetter level, ClipContext context) {
        Vec3 from = context.getFrom();
        Vec3 to = context.getTo();
        LongSet crossed = new LongOpenHashSet();
        List<BlockHitResult> deferred = new ArrayList<>();
        BlockHitResult cell = BlockGetter.traverseBlocks(from, to, context, (clip, cursor) -> {
            // The traversal moves its position on; a deferred hit must keep its own.
            BlockPos pos = cursor.immutable();
            crossed.add(pos.asLong());
            BlockHitResult hit = clipCell(level, clip, pos);
            if (hit == null || insideCell(hit, pos)) return hit;
            // A hit outside the cell may lie behind a block the ray has yet to cross.
            deferred.add(hit);
            return null;
        }, clip -> null);

        List<BlockPos> reaching = new ArrayList<>();
        BlockHitResult nearest = cell;
        for (BlockHitResult hit : deferred) nearest = nearer(from, nearest, hit);
        LongSet checked = new LongOpenHashSet(crossed);
        BlockPos.MutableBlockPos neighbour = new BlockPos.MutableBlockPos();
        for (LongIterator cells = crossed.iterator(); cells.hasNext(); ) {
            BlockPos pos = BlockPos.of(cells.nextLong());
            for (int x = -1; x <= 1; x++) {
                for (int y = -1; y <= 1; y++) {
                    for (int z = -1; z <= 1; z++) {
                        neighbour.setWithOffset(pos, x, y, z);
                        if (!checked.add(neighbour.asLong())) continue;
                        BlockState state = level.getBlockState(neighbour);
                        if (!reachesPastCell(level, neighbour, state)) continue;
                        BlockPos found = neighbour.immutable();
                        reaching.add(found);
                        nearest = nearer(from, nearest, clipBlock(level, context, found, state, from, to));
                    }
                }
            }
        }

        if (nearest == null) {
            Vec3 back = from.subtract(to);
            nearest = BlockHitResult.miss(to, Direction.getApproximateNearest(back.x, back.y, back.z), BlockPos.containing(to));
        }
        return new Trace(cell, List.copyOf(reaching), nearest);
    }

    /**
     * Where a segment enters a block's geometry, or {@code null} when it misses or the block has no
     * geometry.
     */
    public static @Nullable BlockHitResult clipGeometry(BlockGetter level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
        List<PlacedGeometry> geometry = geometry(level, pos, state);
        return geometry == null ? null : clip(geometry, pos, from, to);
    }

    /**
     * Whether a block's geometry reaches past its cell, so rays through the neighbouring cells must
     * test it too, as for an {@link IHaveBigOutline} block.
     */
    public static boolean overhangs(BlockGetter level, BlockPos pos, BlockState state) {
        List<PlacedGeometry> geometry = geometry(level, pos, state);
        AABB bounds = geometry == null ? null : PlacedGeometry.bounds(geometry);
        return bounds != null && (bounds.minX < 0 || bounds.minY < 0 || bounds.minZ < 0
                || bounds.maxX > 1 || bounds.maxY > 1 || bounds.maxZ > 1);
    }

    /** Whether rays through the cells around a block must test it: {@link IHaveBigOutline}, or overhanging geometry. */
    public static boolean reachesPastCell(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof IHaveBigOutline || overhangs(level, pos, state);
    }

    /** The block, or the fluid when it is nearer, that a ray meets in one cell. */
    private static @Nullable BlockHitResult clipCell(BlockGetter level, ClipContext clip, BlockPos pos) {
        Vec3 from = clip.getFrom();
        Vec3 to = clip.getTo();
        BlockState state = level.getBlockState(pos);
        FluidState fluid = level.getFluidState(pos);
        BlockHitResult blockHit = clipBlock(level, clip, pos, state, from, to);
        BlockHitResult fluidHit = clip.getFluidShape(fluid, level, pos).clip(from, to, pos);
        return nearer(from, blockHit, fluidHit);
    }

    private static @Nullable BlockHitResult clipBlock(BlockGetter level, ClipContext clip, BlockPos pos, BlockState state,
                                                      Vec3 from, Vec3 to) {
        List<PlacedGeometry> geometry = geometry(level, pos, state);
        if (geometry != null) return clip(geometry, pos, from, to);
        return level.clipWithInteractionOverride(from, to, pos, clip.getBlockShape(state, level, pos), state);
    }

    private static @Nullable BlockHitResult clip(List<PlacedGeometry> geometry, BlockPos pos, Vec3 from, Vec3 to) {
        Vec3 origin = Vec3.atLowerCornerOf(pos);
        return PlacedGeometry.clip(geometry, from.subtract(origin), to.subtract(origin))
                .map(hit -> new BlockHitResult(hit.location().add(origin), hit.face(), pos, false))
                .orElse(null);
    }

    /** The nearer of two hits to {@code from}, the first on a tie; a miss or {@code null} loses. */
    private static @Nullable BlockHitResult nearer(Vec3 from, @Nullable BlockHitResult first, @Nullable BlockHitResult second) {
        if (second == null || second.getType() == HitResult.Type.MISS) return first;
        if (first == null || first.getType() == HitResult.Type.MISS) return second;
        return from.distanceToSqr(second.getLocation()) < from.distanceToSqr(first.getLocation()) ? second : first;
    }

    private static boolean insideCell(BlockHitResult hit, BlockPos pos) {
        Vec3 location = hit.getLocation();
        return location.x >= pos.getX() - CELL_EPSILON && location.x <= pos.getX() + 1 + CELL_EPSILON
                && location.y >= pos.getY() - CELL_EPSILON && location.y <= pos.getY() + 1 + CELL_EPSILON
                && location.z >= pos.getZ() - CELL_EPSILON && location.z <= pos.getZ() + 1 + CELL_EPSILON;
    }

    private static @Nullable List<PlacedGeometry> geometry(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof GlueBlock block ? block.getGeometry(state, level, pos) : null;
    }

    /**
     * What one ray cast found.
     *
     * @param cell     the hit among the cells the ray crosses, as vanilla finds it but on geometry, or
     *                 {@code null}
     * @param reaching the blocks next to those cells that reach past their own, all tested too
     * @param result   the nearest hit of all, or a miss at the ray's end
     */
    public record Trace(@Nullable BlockHitResult cell, List<BlockPos> reaching, BlockHitResult result) {
    }
}
