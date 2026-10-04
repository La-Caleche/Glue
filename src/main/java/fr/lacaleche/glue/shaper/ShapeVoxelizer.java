package fr.lacaleche.glue.shaper;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/** Turns transformed {@link ShapeGeometry} into a {@link VoxelShape}; see {@link ShapeGeometry#toShape}. */
final class ShapeVoxelizer {

    static final int MAX_RESOLUTION = 64;

    /** Voxels one conversion may sample, so a huge or badly scaled box fails instead of stalling. */
    static final int MAX_VOXELS = 1 << 22;

    /** Below this, a matrix entry is treated as zero when deciding whether a box stays aligned. */
    private static final float ALIGNMENT_EPSILON = 1e-5f;

    /** Corners within this distance of the snapping grid land on it, absorbing float rotation error. */
    private static final double SNAP_EPSILON = 1e-5;
    private static final double SNAP_GRID = 4096;

    /** Grid below which a voxel is filled from the pixel centres it holds rather than its own centre. */
    private static final int PIXELS = 16;

    /** Tolerance of the inside test, so a voxel centre on a box face counts as inside. */
    private static final float INSIDE_EPSILON = 1e-4f;

    private final int resolution;

    ShapeVoxelizer(int resolution) {
        if (resolution < 1 || resolution > MAX_RESOLUTION) {
            throw new IllegalArgumentException("Resolution must be between 1 and " + MAX_RESOLUTION + ": " + resolution);
        }
        this.resolution = resolution;
    }

    VoxelShape voxelize(ShapeGeometry geometry, Matrix4fc transform) {
        List<AABB> boxes = new ArrayList<>();
        List<OrientedBox> unaligned = new ArrayList<>();
        for (GeometryBox box : geometry.boxes()) {
            Matrix4f toWorld = new Matrix4f(transform).mul(box.matrix());
            if (isAxisAligned(toWorld)) {
                boxes.add(transformedBounds(box.box(), toWorld));
            } else if (Math.abs(toWorld.determinant()) >= 1e-9f) {
                unaligned.add(new OrientedBox(box.box(), toWorld));
            }
        }
        if (!unaligned.isEmpty()) boxes.addAll(sample(unaligned));
        return union(boxes.stream().map(Shapes::create).toList());
    }

    /**
     * The optimized union of several shapes, joined in pairs: joining one at a time grows a grid
     * that every later join copies, which takes tens of milliseconds for a detailed model.
     */
    static VoxelShape union(List<VoxelShape> shapes) {
        if (shapes.isEmpty()) return Shapes.empty();
        List<VoxelShape> level = shapes;
        while (level.size() > 1) {
            List<VoxelShape> next = new ArrayList<>((level.size() + 1) / 2);
            for (int i = 0; i < level.size(); i += 2) {
                next.add(i + 1 < level.size() ? Shapes.joinUnoptimized(level.get(i), level.get(i + 1), BooleanOp.OR) : level.get(i));
            }
            level = next;
        }
        return level.getFirst().optimize();
    }

    /** The exact bounds of {@code box} transformed by {@code matrix}, snapped onto a fine grid. */
    static AABB transformedBounds(AABB box, Matrix4fc matrix) {
        Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
        Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
        Vector3f corner = new Vector3f();
        for (int i = 0; i < 8; i++) {
            matrix.transformPosition(
                    (float) ((i & 1) == 0 ? box.minX : box.maxX),
                    (float) ((i & 2) == 0 ? box.minY : box.maxY),
                    (float) ((i & 4) == 0 ? box.minZ : box.maxZ),
                    corner);
            min.min(corner);
            max.max(corner);
        }
        return new AABB(snap(min.x), snap(min.y), snap(min.z), snap(max.x), snap(max.y), snap(max.z));
    }

    /**
     * Whether the transform maps each local axis onto a world axis, so a box stays a box: each
     * column of the linear part has exactly one non-zero entry.
     */
    private static boolean isAxisAligned(Matrix4fc matrix) {
        for (int column = 0; column < 3; column++) {
            int nonZero = 0;
            for (int row = 0; row < 3; row++) {
                if (Math.abs(matrix.get(column, row)) > ALIGNMENT_EPSILON) nonZero++;
            }
            if (nonZero != 1) return false;
        }
        return true;
    }

    private List<AABB> sample(List<OrientedBox> boxes) {
        AABB bounds = null;
        for (OrientedBox box : boxes) {
            AABB boxBounds = transformedBounds(box.box(), box.toWorld());
            bounds = bounds == null ? boxBounds : bounds.minmax(boxBounds);
        }

        Grid grid = new Grid(bounds, this.resolution);
        BitSet filled = new BitSet(grid.size());
        Vector3f local = new Vector3f();
        int samples = Math.max(1, PIXELS / this.resolution);
        for (OrientedBox box : boxes) {
            Matrix4f toLocal = new Matrix4f(box.toWorld()).invert();
            AABB boxBounds = transformedBounds(box.box(), box.toWorld());
            for (int z = grid.firstCell(boxBounds.minZ, 2); z <= grid.lastCell(boxBounds.maxZ, 2); z++) {
                for (int y = grid.firstCell(boxBounds.minY, 1); y <= grid.lastCell(boxBounds.maxY, 1); y++) {
                    for (int x = grid.firstCell(boxBounds.minX, 0); x <= grid.lastCell(boxBounds.maxX, 0); x++) {
                        int index = grid.index(x, y, z);
                        if (!filled.get(index) && containsSample(box, toLocal, grid, x, y, z, samples, local)) filled.set(index);
                    }
                }
            }
        }
        return grid.merge(filled);
    }

    /** Whether the voxel's centre, or with {@code samples > 1} any of its sample points, lies inside the box. */
    private static boolean containsSample(OrientedBox box, Matrix4fc toLocal, Grid grid, int x, int y, int z,
                                          int samples, Vector3f local) {
        for (int i = 0; i < samples; i++) {
            for (int j = 0; j < samples; j++) {
                for (int k = 0; k < samples; k++) {
                    toLocal.transformPosition(grid.sample(x, i, samples, 0), grid.sample(y, j, samples, 1),
                            grid.sample(z, k, samples, 2), local);
                    if (contains(box.box(), local)) return true;
                }
            }
        }
        return false;
    }

    private static boolean contains(AABB box, Vector3f point) {
        return point.x >= box.minX - INSIDE_EPSILON && point.x <= box.maxX + INSIDE_EPSILON
                && point.y >= box.minY - INSIDE_EPSILON && point.y <= box.maxY + INSIDE_EPSILON
                && point.z >= box.minZ - INSIDE_EPSILON && point.z <= box.maxZ + INSIDE_EPSILON;
    }

    private static double snap(float value) {
        double snapped = Math.round(value * SNAP_GRID) / SNAP_GRID;
        return Math.abs(snapped - value) < SNAP_EPSILON ? snapped : value;
    }

    private record OrientedBox(AABB box, Matrix4fc toWorld) {
    }

    /** A voxel grid covering some bounds, cells indexed from an integer origin. */
    private static final class Grid {

        private final int resolution;
        private final int[] origin;
        private final int[] size;

        Grid(AABB bounds, int resolution) {
            this.resolution = resolution;
            this.origin = new int[]{
                    (int) Math.floor(bounds.minX * resolution),
                    (int) Math.floor(bounds.minY * resolution),
                    (int) Math.floor(bounds.minZ * resolution)};
            this.size = new int[]{
                    (int) Math.ceil(bounds.maxX * resolution) - this.origin[0],
                    (int) Math.ceil(bounds.maxY * resolution) - this.origin[1],
                    (int) Math.ceil(bounds.maxZ * resolution) - this.origin[2]};
            long voxels = (long) this.size[0] * this.size[1] * this.size[2];
            if (voxels > MAX_VOXELS) {
                throw new IllegalArgumentException("Geometry spans " + voxels + " voxels at resolution "
                        + resolution + ", more than " + MAX_VOXELS);
            }
        }

        int size() {
            return this.size[0] * this.size[1] * this.size[2];
        }

        /** The first cell whose centre may lie at or after {@code coordinate} on {@code axis}, clamped. */
        int firstCell(double coordinate, int axis) {
            return Math.max(0, (int) Math.floor(coordinate * this.resolution) - this.origin[axis]);
        }

        int lastCell(double coordinate, int axis) {
            return Math.min(this.size[axis] - 1, (int) Math.ceil(coordinate * this.resolution) - this.origin[axis] - 1);
        }

        /** The centre of sub-cell {@code sample} of {@code samples} along {@code axis}; one sample is the cell centre. */
        float sample(int cell, int sample, int samples, int axis) {
            return (this.origin[axis] + cell + (sample + 0.5f) / samples) / this.resolution;
        }

        int index(int x, int y, int z) {
            return x + this.size[0] * (y + this.size[1] * z);
        }

        /**
         * Greedy merge: from each unclaimed filled voxel, grow a run along X, then extend it along Y
         * while every row is filled and unclaimed, then along Z while every layer is.
         */
        List<AABB> merge(BitSet filled) {
            BitSet claimed = new BitSet(size());
            List<AABB> boxes = new ArrayList<>();
            for (int start = filled.nextSetBit(0); start >= 0; start = filled.nextSetBit(start + 1)) {
                if (claimed.get(start)) continue;
                int x0 = start % this.size[0];
                int y0 = (start / this.size[0]) % this.size[1];
                int z0 = start / (this.size[0] * this.size[1]);

                int x1 = x0;
                while (x1 + 1 < this.size[0] && isFree(filled, claimed, x1 + 1, y0, z0)) x1++;
                int y1 = y0;
                while (y1 + 1 < this.size[1] && isFreeRange(filled, claimed, x0, x1, y1 + 1, y1 + 1, z0)) y1++;
                int z1 = z0;
                while (z1 + 1 < this.size[2] && isFreeRange(filled, claimed, x0, x1, y0, y1, z1 + 1)) z1++;

                for (int z = z0; z <= z1; z++) {
                    for (int y = y0; y <= y1; y++) {
                        claimed.set(index(x0, y, z), index(x1, y, z) + 1);
                    }
                }
                boxes.add(new AABB(
                        (double) (this.origin[0] + x0) / this.resolution,
                        (double) (this.origin[1] + y0) / this.resolution,
                        (double) (this.origin[2] + z0) / this.resolution,
                        (double) (this.origin[0] + x1 + 1) / this.resolution,
                        (double) (this.origin[1] + y1 + 1) / this.resolution,
                        (double) (this.origin[2] + z1 + 1) / this.resolution));
            }
            return boxes;
        }

        private boolean isFree(BitSet filled, BitSet claimed, int x, int y, int z) {
            int index = index(x, y, z);
            return filled.get(index) && !claimed.get(index);
        }

        private boolean isFreeRange(BitSet filled, BitSet claimed, int x0, int x1, int y0, int y1, int z) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    if (!isFree(filled, claimed, x, y, z)) return false;
                }
            }
            return true;
        }
    }
}
