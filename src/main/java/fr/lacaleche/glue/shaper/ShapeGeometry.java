package fr.lacaleche.glue.shaper;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Boxes in block units that can be moved, rotated and scaled, then turned into a {@link VoxelShape}
 * or ray-cast exactly.
 *
 * <p>Geometry is plain, immutable data, available on both sides: it comes from a block's shape
 * ({@link #of(VoxelShape)}), from model elements, or from code. Conversion is not cached; callers
 * keep the shapes they reuse. Only {@link #alignedParts()}, which outlines draw every frame, is
 * built once per geometry.</p>
 */
public final class ShapeGeometry {

    /** Voxels per block when a transform leaves a box unaligned: one per pixel. */
    public static final int DEFAULT_RESOLUTION = 16;

    public static final ShapeGeometry EMPTY = new ShapeGeometry(List.of());

    private final List<GeometryBox> boxes;
    private volatile @Nullable List<AlignedPart> alignedParts;

    /** @param boxes the boxes, in no particular order; may overlap */
    public ShapeGeometry(List<GeometryBox> boxes) {
        this.boxes = List.copyOf(boxes);
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

    public List<GeometryBox> boxes() {
        return this.boxes;
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
     * as few boxes as the greedy merge finds. On a grid coarser than a pixel, a voxel is filled when
     * any pixel centre in it lies inside, so a part thinner than a voxel still covers it. Boxes with
     * no thickness cover nothing.</p>
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
     * Where the segment from {@code from} to {@code to} first enters the transformed geometry,
     * tested against the boxes themselves rather than a voxel approximation, with the block side the
     * hit face points to the most. The segment is in the geometry's own block-local space, not world
     * coordinates: transforms are single precision.
     */
    public Optional<GeometryHit> clip(Matrix4fc transform, Vec3 from, Vec3 to) {
        GeometryHit nearest = null;
        double nearestDistance = Double.POSITIVE_INFINITY;
        for (GeometryBox box : this.boxes) {
            Matrix4f toWorld = new Matrix4f(transform).mul(box.matrix());
            if (Math.abs(toWorld.determinant()) < 1e-9f) continue;

            Matrix4f toLocal = new Matrix4f(toWorld).invert();
            BlockHitResult hit = AABB.clip(List.of(box.box()), transform(toLocal, from), transform(toLocal, to), BlockPos.ZERO);
            if (hit == null) continue;

            Vec3 point = transform(toWorld, hit.getLocation());
            double distance = point.distanceToSqr(from);
            if (distance < nearestDistance) {
                nearest = new GeometryHit(point, face(toWorld, hit.getDirection()), 0);
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * The geometry as axis-aligned shapes, each with the matrix that places it: the unrotated boxes
     * together, then the boxes sharing each element rotation. Drawing each shape's edges through its
     * matrix draws the geometry exactly, merged edges included. Built on the first call and kept,
     * since an outline asks for it every frame. The list is immutable.
     */
    public List<AlignedPart> alignedParts() {
        List<AlignedPart> parts = this.alignedParts;
        if (parts == null) {
            parts = buildAlignedParts();
            this.alignedParts = parts;
        }
        return parts;
    }

    private List<AlignedPart> buildAlignedParts() {
        List<VoxelShape> aligned = new ArrayList<>();
        Map<GeometryBox.Rotation, List<VoxelShape>> rotated = new LinkedHashMap<>();
        for (GeometryBox box : this.boxes) {
            VoxelShape shape = Shapes.create(box.box());
            if (box.rotation() == null) aligned.add(shape);
            else rotated.computeIfAbsent(box.rotation(), rotation -> new ArrayList<>()).add(shape);
        }

        List<AlignedPart> parts = new ArrayList<>();
        if (!aligned.isEmpty()) parts.add(new AlignedPart(new Matrix4f(), ShapeVoxelizer.union(aligned)));
        rotated.forEach((rotation, shapes) -> parts.add(new AlignedPart(rotation.matrix(), ShapeVoxelizer.union(shapes))));
        return List.copyOf(parts);
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

    private static Direction face(Matrix4fc toWorld, Direction localFace) {
        Vector3f normal = toWorld.normal(new Matrix3f()).transform(new Vector3f(localFace.getUnitVec3f()));
        return Direction.getApproximateNearest(normal.x, normal.y, normal.z);
    }

    private static Vec3 transform(Matrix4fc matrix, Vec3 point) {
        Vector3f transformed = matrix.transformPosition((float) point.x, (float) point.y, (float) point.z, new Vector3f());
        return new Vec3(transformed.x, transformed.y, transformed.z);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShapeGeometry geometry && this.boxes.equals(geometry.boxes);
    }

    @Override
    public int hashCode() {
        return this.boxes.hashCode();
    }

    @Override
    public String toString() {
        return "ShapeGeometry" + this.boxes;
    }

    /**
     * Axis-aligned boxes and the matrix that places them in the geometry's space, with the edges of
     * their union listed once so that drawing them does not walk the shape again.
     */
    public static final class AlignedPart {

        private final Matrix4fc matrix;
        private final VoxelShape shape;
        private final double[] edges;

        /**
         * @param matrix where the shape's boxes go: identity, or an element rotation
         * @param shape  the boxes before that matrix
         */
        public AlignedPart(Matrix4fc matrix, VoxelShape shape) {
            this.matrix = new Matrix4f(matrix);
            this.shape = shape;
            List<double[]> edges = new ArrayList<>();
            shape.forAllEdges((x1, y1, z1, x2, y2, z2) -> edges.add(new double[]{x1, y1, z1, x2, y2, z2}));
            this.edges = new double[edges.size() * 6];
            for (int i = 0; i < edges.size(); i++) System.arraycopy(edges.get(i), 0, this.edges, i * 6, 6);
        }

        public Matrix4fc matrix() {
            return this.matrix;
        }

        public VoxelShape shape() {
            return this.shape;
        }

        /** The shape's edges, as {@link VoxelShape#forAllEdges} gives them, from the list kept. */
        public void forAllEdges(Shapes.DoubleLineConsumer consumer) {
            for (int i = 0; i < this.edges.length; i += 6) {
                consumer.consume(this.edges[i], this.edges[i + 1], this.edges[i + 2],
                        this.edges[i + 3], this.edges[i + 4], this.edges[i + 5]);
            }
        }
    }
}
