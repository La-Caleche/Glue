package fr.lacaleche.composite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.data.components.TransformationComponent;
import fr.lacaleche.glue.shaper.BlockShapeProvider;
import fr.lacaleche.glue.shaper.BlockShapes;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.List;

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

    /**
     * What the part looks like in its cell: its block's generated geometry when it has some, its
     * block's outline otherwise, moved by the part's transform.
     */
    public List<PlacedGeometry> geometry() {
        Matrix4f matrix = matrix();
        List<PlacedGeometry> generated = BlockShapes.geometry(this.state);
        if (generated != null) return generated.stream().map(geometry -> geometry.placed(matrix)).toList();
        return List.of(new PlacedGeometry(ShapeGeometry.of(blockOutline()), matrix));
    }

    /** The part's outline once transformed, voxelized where it leaves the axes. */
    public VoxelShape outline() {
        return PlacedGeometry.toShape(geometry(), ShapeGeometry.DEFAULT_RESOLUTION);
    }

    /**
     * The part's collision once transformed, voxelized where it leaves the axes: from its block's
     * generated collision models at their resolution when the block collides as generated, from its
     * collision shape otherwise.
     */
    public VoxelShape collision() {
        VoxelShape collision = this.state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        List<PlacedGeometry> generated = BlockShapes.collisionGeometry(this.state);
        // The generated shape is cached, so the same instance means the block did not override it.
        if (generated == null || collision != BlockShapes.collision(this.state)) {
            return ShapeGeometry.of(collision).toShape(matrix(), BlockShapeProvider.Rule.DEFAULT_COLLISION_RESOLUTION);
        }
        Matrix4f matrix = matrix();
        return PlacedGeometry.toShape(generated.stream().map(geometry -> geometry.placed(matrix)).toList(),
                BlockShapes.collisionResolution(this.state));
    }

    /** The bounds of the part's geometry, or {@code null} when it has none. */
    public @Nullable AABB bounds() {
        return PlacedGeometry.bounds(geometry());
    }

    private VoxelShape blockOutline() {
        return this.state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }
}
