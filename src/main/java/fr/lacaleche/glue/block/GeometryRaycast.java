package fr.lacaleche.glue.block;

import fr.lacaleche.glue.shaper.PlacedGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ray casts that hit a {@link GlueBlock} on its geometry: a ray through the gap between a chair's
 * legs reaches what stands behind, and a ray grazing a part turned by 22.5 degrees hits it where it
 * is drawn, not where its voxel shape steps.
 */
public final class GeometryRaycast {

    private GeometryRaycast() {
    }

    /**
     * {@link BlockGetter#clip}, except that a {@link GlueBlock} with geometry is hit on that
     * geometry instead of the context's block shape. Meant for outline contexts, such as the
     * player's pick.
     */
    public static BlockHitResult clip(BlockGetter level, ClipContext context) {
        return BlockGetter.traverseBlocks(context.getFrom(), context.getTo(), context, (clip, pos) -> {
            Vec3 from = clip.getFrom();
            Vec3 to = clip.getTo();
            BlockState state = level.getBlockState(pos);
            FluidState fluid = level.getFluidState(pos);
            BlockHitResult blockHit = clipBlock(level, clip, pos, state, from, to);
            BlockHitResult fluidHit = clip.getFluidShape(fluid, level, pos).clip(from, to, pos);
            double blockDistance = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
            double fluidDistance = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
            return blockDistance <= fluidDistance ? blockHit : fluidHit;
        }, clip -> {
            Vec3 back = clip.getFrom().subtract(clip.getTo());
            return BlockHitResult.miss(clip.getTo(), Direction.getApproximateNearest(back.x, back.y, back.z), BlockPos.containing(clip.getTo()));
        });
    }

    /**
     * Where a segment enters a block's geometry, or {@code null} when it misses or the block has no
     * geometry; see {@link #hasGeometry}.
     */
    public static @Nullable BlockHitResult clipGeometry(BlockGetter level, BlockPos pos, BlockState state, Vec3 from, Vec3 to) {
        List<PlacedGeometry> geometry = geometry(level, pos, state);
        return geometry == null ? null : clip(geometry, pos, from, to);
    }

    /** Whether the block at a position is a {@link GlueBlock} with geometry. */
    public static boolean hasGeometry(BlockGetter level, BlockPos pos, BlockState state) {
        return geometry(level, pos, state) != null;
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

    private static @Nullable List<PlacedGeometry> geometry(BlockGetter level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof GlueBlock block ? block.getGeometry(state, level, pos) : null;
    }
}
