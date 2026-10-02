package fr.lacaleche.glue.shaper;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Boxes in block units that can be moved, rotated and scaled, then turned into a {@link VoxelShape}
 * or ray-cast exactly.
 *
 * <p>Geometry is plain data, available on both sides: it comes from a block's shape
 * ({@link #of(VoxelShape)}), from model elements, or from code. Conversion is not cached; callers
 * keep the shapes they reuse.</p>
 *
 * @param boxes the boxes, in no particular order; may overlap
 */
public record ShapeGeometry(List<GeometryBox> boxes) {

    /** Voxels per block when a transform leaves a box unaligned: one per pixel. */
    public static final int DEFAULT_RESOLUTION = 16;

    public static final ShapeGeometry EMPTY = new ShapeGeometry(List.of());

    public ShapeGeometry {
        boxes = List.copyOf(boxes);
    }

    public static ShapeGeometry of(GeometryBox... boxes) {
        return new ShapeGeometry(Arrays.asList(boxes));
    }

    /** The boxes of a shape, axis-aligned. */
    public static ShapeGeometry of(VoxelShape shape) {
        return new ShapeGeometry(shape.toAabbs().stream().map(GeometryBox::of).toList());
    }

    /**
     * {@code transform} applied about the block centre instead of the origin, the way blockstate
     * rotations and block display transforms turn a block in place.
     */
    public static Matrix4f aboutCentre(Matrix4fc transform) {
        return new Matrix4f().translation(0.5f, 0.5f, 0.5f).mul(transform).translate(-0.5f, -0.5f, -0.5f);
    }

    /** The untransformed shape at the default resolution. */
    public VoxelShape toShape() {
        return toShape(new Matrix4f(), DEFAULT_RESOLUTION);
    }

    /**
     * The shape this geometry covers once transformed.
     *
     * <p>A box that stays axis-aligned (no rotation, quarter turns, axis scales) is converted
     * exactly. Any other box is sampled on a grid of {@code resolution} voxels per block, a voxel
     * being filled when its centre lies inside the box, and the filled voxels are merged back into
     * as few boxes as the greedy merge finds. Boxes with no thickness cover nothing.</p>
     *
     * @param transform  an affine transform in block units
     * @param resolution voxels per block for unaligned boxes, from 1 to {@value ShapeVoxelizer#MAX_RESOLUTION}
     * @throws IllegalArgumentException if the resolution is out of range or the unaligned boxes span
     *                                  more voxels than one conversion allows
     */
    public VoxelShape toShape(Matrix4fc transform, int resolution) {
        return new ShapeVoxelizer(resolution).voxelize(this, transform);
    }

    /**
     * The point where the segment from {@code from} to {@code to} first enters the transformed
     * geometry, tested against the boxes themselves rather than a voxel approximation. The segment is
     * in the geometry's own block-local space, not world coordinates: transforms are single
     * precision.
     */
    public Optional<Vec3> clip(Matrix4fc transform, Vec3 from, Vec3 to) {
        Vec3 nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (GeometryBox box : this.boxes) {
            Matrix4f toWorld = new Matrix4f(transform).mul(box.matrix());
            if (Math.abs(toWorld.determinant()) < 1e-9f) continue;

            Matrix4f toLocal = new Matrix4f(toWorld).invert();
            Optional<Vec3> hit = box.box().clip(transform(toLocal, from), transform(toLocal, to));
            if (hit.isEmpty()) continue;

            Vec3 point = transform(toWorld, hit.get());
            double distance = point.distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = point;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** The bounds of the transformed geometry, or {@code null} when it has no boxes. */
    public @Nullable AABB bounds(Matrix4fc transform) {
        AABB bounds = null;
        for (GeometryBox box : this.boxes) {
            AABB boxBounds = ShapeVoxelizer.transformedBounds(box.box(), new Matrix4f(transform).mul(box.matrix()));
            bounds = bounds == null ? boxBounds : bounds.minmax(boxBounds);
        }
        return bounds;
    }

    private static Vec3 transform(Matrix4fc matrix, Vec3 point) {
        Vector3f transformed = matrix.transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }
}
