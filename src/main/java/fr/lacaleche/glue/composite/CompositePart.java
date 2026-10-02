package fr.lacaleche.glue.composite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * One block drawn inside a composite cell: its state, and the transform that moves, turns and scales
 * it about the cell centre, in block units.
 *
 * <p>A part is decorative. Its block keeps its model and shape, but no block entity, ticking or
 * interaction of its own.</p>
 */
public record CompositePart(BlockState state, TransformationComponent transform) {

    /** Decoding fails for a block that is no longer registered, which drops the part from a cell. */
    public static final Codec<CompositePart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("state").forGetter(CompositePart::state),
            TransformationComponent.CODEC.optionalFieldOf("transform", TransformationComponent.DEFAULT)
                    .forGetter(CompositePart::transform)
    ).apply(instance, CompositePart::new));

    private static final float IDENTITY_TOLERANCE = 1e-6f;

    public CompositePart(BlockState state) {
        this(state, TransformationComponent.DEFAULT);
    }

    /** The transform as a matrix in block units, applied about the cell centre. */
    public Matrix4f matrix() {
        return ShapeGeometry.aboutCentre(this.transform.toTransformation().getMatrix());
    }

    /** Whether the part sits exactly where a plain block of its state would. */
    public boolean isIdentity() {
        return matrix().equals(new Matrix4f(), IDENTITY_TOLERANCE);
    }

    /** The part's outline once transformed, voxelized where it leaves the axes. */
    public VoxelShape outline() {
        return transformed(this.state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()));
    }

    /** The part's collision once transformed, voxelized where it leaves the axes. */
    public VoxelShape collision() {
        return transformed(this.state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()));
    }

    /** The bounds of the transformed outline, or {@code null} when the block has no outline. */
    public @Nullable AABB bounds() {
        return ShapeGeometry.of(this.state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()))
                .bounds(matrix());
    }

    private VoxelShape transformed(VoxelShape shape) {
        return ShapeGeometry.of(shape).toShape(matrix(), ShapeGeometry.DEFAULT_RESOLUTION);
    }
}
