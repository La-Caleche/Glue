package fr.lacaleche.glue.shaper;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.List;
import java.util.Optional;

/**
 * Geometry where a block draws it: a model's boxes and the transform, in block units, that places
 * them in the block, such as a blockstate rotation or a composite part's transform.
 *
 * <p>This is what a block looks like, exactly: outlines and ray casts use it at any angle, while
 * collision, which only knows axis-aligned boxes, uses its voxelized {@link #toShape}.</p>
 */
public record PlacedGeometry(ShapeGeometry geometry, Matrix4fc transform) {

    public PlacedGeometry {
        transform = new Matrix4f(transform);
    }

    /** The geometry where it stands, untransformed. */
    public static PlacedGeometry of(ShapeGeometry geometry) {
        return new PlacedGeometry(geometry, new Matrix4f());
    }

    /** This geometry moved again by {@code outer}, applied after its own transform. */
    public PlacedGeometry placed(Matrix4fc outer) {
        return new PlacedGeometry(this.geometry, new Matrix4f(outer).mul(this.transform));
    }

    /** The voxelized shape; see {@link ShapeGeometry#toShape(Matrix4fc, int)}. */
    public VoxelShape toShape(int resolution) {
        return this.geometry.toShape(this.transform, resolution);
    }

    /** The bounds of the placed boxes, or {@code null} when there are none. */
    public @Nullable AABB bounds() {
        return this.geometry.bounds(this.transform);
    }

    /** See {@link ShapeGeometry#clip}. */
    public Optional<GeometryHit> clip(Vec3 from, Vec3 to) {
        return this.geometry.clip(this.transform, from, to);
    }

    /** The union of the voxelized shapes of several placed geometries. */
    public static VoxelShape toShape(List<PlacedGeometry> geometries, int resolution) {
        VoxelShape shape = Shapes.empty();
        for (PlacedGeometry geometry : geometries) shape = Shapes.joinUnoptimized(shape, geometry.toShape(resolution), BooleanOp.OR);
        return shape.optimize();
    }

    /** The bounds of several placed geometries, or {@code null} when none has a box. */
    public static @Nullable AABB bounds(List<PlacedGeometry> geometries) {
        AABB bounds = null;
        for (PlacedGeometry geometry : geometries) {
            AABB geometryBounds = geometry.bounds();
            if (geometryBounds != null) bounds = bounds == null ? geometryBounds : bounds.minmax(geometryBounds);
        }
        return bounds;
    }

    /**
     * The nearest entry point of a segment into several placed geometries, with the index of the one
     * it enters in {@link GeometryHit#placement()}.
     */
    public static Optional<GeometryHit> clip(List<PlacedGeometry> geometries, Vec3 from, Vec3 to) {
        GeometryHit nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < geometries.size(); i++) {
            Optional<GeometryHit> hit = geometries.get(i).clip(from, to);
            if (hit.isEmpty()) continue;

            double distance = hit.get().location().distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = new GeometryHit(hit.get().location(), hit.get().face(), i);
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }
}
