package fr.lacaleche.glue.block;

import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import org.joml.Matrix4f;

import java.util.stream.IntStream;

/**
 * Blocks turned in sixteen steps of 22.5 degrees by a {@code 0..15} property, such as
 * {@code BlockStateProperties.ROTATION_16}: placement, structure rotation and mirroring, and the
 * turn itself, shared by the block's shapes and its model so that both stand the same way.
 *
 * <p>A block keeps one blockstate model for every step. On the client,
 * {@code GlueBlockModels.rotation16} turns that model in the chunk mesh, and
 * {@code BlockShapeProvider.Rule.rotation16} turns the generated shapes to match.</p>
 */
public final class Rotation16 {

    public static final int STEPS = 16;

    private Rotation16() {
    }

    /** The step facing the player who places the block. */
    public static int forPlacement(BlockPlaceContext context) {
        return RotationSegment.convertToSegment(context.getRotation());
    }

    public static BlockState rotate(BlockState state, IntegerProperty property, Rotation rotation) {
        return state.setValue(property, rotation.rotate(state.getValue(property), STEPS));
    }

    public static BlockState mirror(BlockState state, IntegerProperty property, Mirror mirror) {
        return state.setValue(property, mirror.mirror(state.getValue(property), STEPS));
    }

    /** Degrees the block is turned clockwise seen from above, at a step. */
    public static float degrees(int step) {
        return RotationSegment.convertToDegrees(step);
    }

    /** The turn at a step, about the block centre, in block units. */
    public static Matrix4f matrix(int step) {
        return ShapeGeometry.aboutCentre(new Matrix4f().rotationY((float) Math.toRadians(-degrees(step))));
    }

    /**
     * Checks that a property takes exactly the sixteen steps.
     *
     * @throws IllegalArgumentException if it does not
     */
    public static IntegerProperty check(IntegerProperty property) {
        if (!property.getPossibleValues().equals(IntStream.range(0, STEPS).boxed().toList())) {
            throw new IllegalArgumentException("Property " + property.getName() + " does not range from 0 to " + (STEPS - 1));
        }
        return property;
    }
}
