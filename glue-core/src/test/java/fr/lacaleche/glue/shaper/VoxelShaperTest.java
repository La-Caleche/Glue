package fr.lacaleche.glue.shaper;

import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VoxelShaperTest {

    @Test
    public void get_emptyShaper_returnsNullForAllDirections() {
        VoxelShaper shaper = new VoxelShaper();
        for (Direction dir : Direction.values()) {
            assertNull(shaper.get(dir),
                    "get(" + dir + ") on empty shaper should return null, not throw");
        }
    }

    @Test
    public void get_emptyShaper_returnsNullForAllAxes() {
        VoxelShaper shaper = new VoxelShaper();
        for (Axis axis : Axis.values()) {
            assertNull(shaper.get(axis),
                    "get(" + axis + ") on empty shaper should return null, not throw");
        }
    }

    @Test
    public void withShape_registeredDirection_returnsSameShape() {
        VoxelShape shape = Shapes.empty();
        VoxelShaper shaper = new VoxelShaper().withShape(shape, Direction.NORTH);

        assertSame(shape, shaper.get(Direction.NORTH), "Registered direction should return the exact shape");
        assertNull(shaper.get(Direction.SOUTH), "Unregistered direction should still return null");
    }

    @Test
    public void withShape_calledTwiceOnSameDirection_overwritesFirst() {
        VoxelShape shape1 = Shapes.empty();
        VoxelShape shape2 = Shapes.block();
        VoxelShaper shaper = new VoxelShaper()
                .withShape(shape1, Direction.EAST)
                .withShape(shape2, Direction.EAST);

        assertSame(shape2, shaper.get(Direction.EAST), "Second withShape should overwrite first");
    }

    @Test
    public void withShape_multipleDirections_areStoredIndependently() {
        VoxelShape shapeNorth = Shapes.empty();
        VoxelShape shapeSouth = Shapes.block();
        VoxelShaper shaper = new VoxelShaper()
                .withShape(shapeNorth, Direction.NORTH)
                .withShape(shapeSouth, Direction.SOUTH);

        assertSame(shapeNorth, shaper.get(Direction.NORTH));
        assertSame(shapeSouth, shaper.get(Direction.SOUTH));
        assertNull(shaper.get(Direction.EAST), "Unregistered direction should still return null");
    }

    @Test
    public void axisAsFace_anyAxis_returnsPositiveFace() {
        assertEquals(Direction.EAST, VoxelShaper.axisAsFace(Axis.X));
        assertEquals(Direction.UP, VoxelShaper.axisAsFace(Axis.Y));
        assertEquals(Direction.SOUTH, VoxelShaper.axisAsFace(Axis.Z));
    }

    @Test
    public void horizontalAngleFromDirection_south_returnsZero() {
        assertEquals(0f, VoxelShaper.horizontalAngleFromDirection(Direction.SOUTH), 1e-5f);
    }

    @Test
    public void horizontalAngleFromDirection_anyHorizontal_returnsMultipleOf90() {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            float angle = VoxelShaper.horizontalAngleFromDirection(dir);
            assertTrue(angle >= 0f && angle < 360f,
                    "Horizontal angle for " + dir + " should be in [0, 360)");
            assertEquals(0f, angle % 90f, 1e-4f,
                    "Horizontal angle for " + dir + " should be a multiple of 90°");
        }
    }

    @Test
    public void rotate_sameFromAndTo_returnsSameShape() {
        VoxelShape shape = Shapes.block();
        assertSame(shape, VoxelShaper.rotate(shape, Direction.NORTH, Direction.NORTH),
                "Rotating from==to should return the original shape unchanged");
    }

    @Test
    public void forHorizontal_anyFacing_registersAllHorizontalDirections() {
        VoxelShape shape = Shapes.block();
        VoxelShaper shaper = VoxelShaper.forHorizontal(shape, Direction.SOUTH);

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            assertNotNull(shaper.get(dir), "forHorizontal should register shape for " + dir);
        }
        assertNull(shaper.get(Direction.UP), "forHorizontal should not register UP");
        assertNull(shaper.get(Direction.DOWN), "forHorizontal should not register DOWN");
    }

    @Test
    public void withVerticalShapes_givenShape_registersUpAndDown() {
        VoxelShape upShape = Shapes.block();
        VoxelShaper shaper = new VoxelShaper().withVerticalShapes(upShape);

        assertSame(upShape, shaper.get(Direction.UP), "UP should map to the provided shape");
        assertNotNull(shaper.get(Direction.DOWN), "DOWN should be the rotated complement of UP");
    }
}
