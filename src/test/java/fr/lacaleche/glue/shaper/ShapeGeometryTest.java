package fr.lacaleche.glue.shaper;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ShapeGeometryTest {

    private static final GeometryBox SHELF = GeometryBox.pixels(2, 0, 0, 14, 4, 16);
    private static final GeometryBox BACK_WALL = GeometryBox.pixels(2, 4, 10, 14, 14, 16);

    @Test
    void untransformedGeometryIsTheExactUnionOfItsBoxes() {
        VoxelShape expected = Shapes.or(
                Shapes.box(2 / 16d, 0, 0, 14 / 16d, 4 / 16d, 1),
                Shapes.box(2 / 16d, 4 / 16d, 10 / 16d, 14 / 16d, 14 / 16d, 1));

        assertSameShape(expected, ShapeGeometry.of(SHELF, BACK_WALL).toShape());
    }

    @Test
    void quarterTurnsMatchVoxelShaper() {
        ShapeGeometry geometry = ShapeGeometry.of(SHELF, BACK_WALL);
        VoxelShape north = geometry.toShape();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Matrix4f turn = ShapeGeometry.aboutCentre(
                    new Matrix4f().rotationY((float) Math.toRadians(-facing.toYRot() + Direction.NORTH.toYRot())));

            assertSameShape(VoxelShaper.rotate(north, Direction.NORTH, facing), geometry.toShape(turn, 16),
                    "facing " + facing);
        }
    }

    @Test
    void quarterTurnsStayExactAtTheCoarsestResolution() {
        ShapeGeometry geometry = ShapeGeometry.of(GeometryBox.pixels(1, 0, 3, 7, 5, 16));
        Matrix4f turn = ShapeGeometry.aboutCentre(new Matrix4f().rotationY((float) Math.toRadians(90)));

        VoxelShape shape = geometry.toShape(turn, 1);

        assertEquals(1, shape.toAabbs().size());
        assertSameShape(VoxelShaper.rotate(geometry.toShape(), Direction.NORTH, Direction.WEST), shape);
    }

    @Test
    void translationCanLeaveTheBlock() {
        Matrix4f up = new Matrix4f().translation(0, 1, 0);

        VoxelShape shape = ShapeGeometry.of(GeometryBox.pixels(0, 0, 0, 16, 16, 16)).toShape(up, 16);

        assertEquals(new AABB(0, 1, 0, 1, 2, 1), shape.bounds());
    }

    @Test
    void elementAt45DegreesIsVoxelizedWithinItsRotatedBounds() {
        GeometryBox plank = GeometryBox.pixels(4, 0, 4, 12, 2, 12)
                .rotated(new GeometryBox.Rotation(new Vec3(0.5, 0, 0.5), Direction.Axis.Y, 45, false));

        VoxelShape shape = ShapeGeometry.of(plank).toShape();

        double halfDiagonal = 4 * Math.sqrt(2) / 16;
        AABB bounds = shape.bounds();
        assertTrue(bounds.minX >= 0.5 - halfDiagonal && bounds.maxX <= 0.5 + halfDiagonal, () -> "bounds " + bounds);
        assertTrue(shape.toAabbs().size() > 1, "a diamond needs several boxes");
        double expectedVolume = (8 / 16d) * (8 / 16d) * (2 / 16d);
        assertEquals(expectedVolume, volume(shape), expectedVolume * 0.15);
    }

    @Test
    void elementCornersOutsideTheDiamondAreNotFilled() {
        GeometryBox plank = GeometryBox.pixels(4, 0, 4, 12, 2, 12)
                .rotated(new GeometryBox.Rotation(new Vec3(0.5, 0, 0.5), Direction.Axis.Y, 45, false));

        VoxelShape shape = ShapeGeometry.of(plank).toShape();

        AABB corner = new AABB(0.5 + 3 / 16d, 0, 0.5 + 3 / 16d, 0.5 + 4 / 16d, 2 / 16d, 0.5 + 4 / 16d);
        assertFalse(Shapes.joinIsNotEmpty(shape, Shapes.create(corner), BooleanOp.AND),
                "the unrotated box's corner lies outside the rotated element");
    }

    @Test
    void rescaleStretchesTheTwoOtherAxes() {
        GeometryBox.Rotation plain = new GeometryBox.Rotation(new Vec3(0.5, 0.5, 0.5), Direction.Axis.Y, 45, false);
        GeometryBox.Rotation rescaled = new GeometryBox.Rotation(new Vec3(0.5, 0.5, 0.5), Direction.Axis.Y, 45, true);
        GeometryBox box = GeometryBox.pixels(4, 0, 4, 12, 16, 12);

        AABB plainBounds = ShapeGeometry.of(box.rotated(plain)).bounds(new Matrix4f());
        AABB rescaledBounds = ShapeGeometry.of(box.rotated(rescaled)).bounds(new Matrix4f());

        assertEquals(plainBounds.getXsize() * Math.sqrt(2), rescaledBounds.getXsize(), 1e-4);
        assertEquals(plainBounds.getYsize(), rescaledBounds.getYsize(), 1e-6);
    }

    @Test
    void boxWithoutThicknessCoversNothing() {
        GeometryBox plane = GeometryBox.pixels(0, 8, 0, 16, 8, 16);
        GeometryBox tiltedPlane = plane.rotated(new GeometryBox.Rotation(new Vec3(0.5, 0.5, 0.5), Direction.Axis.X, 22.5f, false));

        assertTrue(ShapeGeometry.of(plane).toShape().isEmpty());
        assertTrue(ShapeGeometry.of(tiltedPlane).toShape().isEmpty());
    }

    @Test
    void shapeRoundTripsThroughGeometry() {
        VoxelShape shape = Shapes.or(Shapes.box(0, 0, 0, 1, 0.25, 1), Shapes.box(0.25, 0.25, 0.25, 0.75, 1, 0.75));

        assertSameShape(shape, ShapeGeometry.of(shape).toShape());
    }

    @Test
    void clipHitsTheRotatedBoxItself() {
        GeometryBox plank = GeometryBox.pixels(4, 0, 4, 12, 2, 12)
                .rotated(new GeometryBox.Rotation(new Vec3(0.5, 0, 0.5), Direction.Axis.Y, 45, false));
        ShapeGeometry geometry = ShapeGeometry.of(plank);

        Optional<Vec3> centre = geometry.clip(new Matrix4f(), new Vec3(0.5, 1, 0.5), new Vec3(0.5, -1, 0.5));
        Optional<Vec3> unrotatedCorner = geometry.clip(new Matrix4f(), new Vec3(0.74, 1, 0.74), new Vec3(0.74, -1, 0.74));

        assertTrue(centre.isPresent());
        assertEquals(2 / 16d, centre.get().y, 1e-5);
        assertTrue(unrotatedCorner.isEmpty(), "the unrotated box's corner is not part of the element");
    }

    @Test
    void clipReturnsTheNearestBox() {
        ShapeGeometry geometry = ShapeGeometry.of(SHELF, BACK_WALL);

        Optional<Vec3> hit = geometry.clip(new Matrix4f(), new Vec3(0.5, 0.5, -1), new Vec3(0.5, 0.5, 2));

        assertTrue(hit.isPresent());
        assertEquals(10 / 16d, hit.get().z, 1e-5);
    }

    @Test
    void emptyGeometryHasNoShapeBoundsOrHit() {
        assertTrue(ShapeGeometry.EMPTY.toShape().isEmpty());
        assertNull(ShapeGeometry.EMPTY.bounds(new Matrix4f()));
        assertTrue(ShapeGeometry.EMPTY.clip(new Matrix4f(), Vec3.ZERO, new Vec3(1, 1, 1)).isEmpty());
    }

    @Test
    void resolutionOutsideItsRangeIsRejected() {
        ShapeGeometry geometry = ShapeGeometry.of(SHELF);

        assertThrows(IllegalArgumentException.class, () -> geometry.toShape(new Matrix4f(), 0));
        assertThrows(IllegalArgumentException.class, () -> geometry.toShape(new Matrix4f(), 65));
    }

    @Test
    void unalignedGeometryTooLargeToSampleIsRejected() {
        Matrix4f hugeTilt = new Matrix4f().scale(200).rotateY((float) Math.toRadians(30));

        assertThrows(IllegalArgumentException.class,
                () -> ShapeGeometry.of(SHELF).toShape(hugeTilt, ShapeGeometry.DEFAULT_RESOLUTION));
    }

    private static double volume(VoxelShape shape) {
        return shape.toAabbs().stream().mapToDouble(box -> box.getXsize() * box.getYsize() * box.getZsize()).sum();
    }

    private static void assertSameShape(VoxelShape expected, VoxelShape actual) {
        assertSameShape(expected, actual, "");
    }

    private static void assertSameShape(VoxelShape expected, VoxelShape actual, String context) {
        assertFalse(Shapes.joinIsNotEmpty(expected, actual, BooleanOp.NOT_SAME),
                () -> context + " expected " + expected.toAabbs() + " but was " + actual.toAabbs());
    }
}
