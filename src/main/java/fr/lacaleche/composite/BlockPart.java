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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.List;

/**
 * A block drawn in a composite cell: its model, and its block entity's renderer from the data the
 * cell keeps for it. Its state is drawn as it is and never changes on its own.
 */
public record BlockPart(BlockState state, TransformationComponent transform) implements CompositePart {

    public static final Codec<BlockPart> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("state").forGetter(BlockPart::state),
            TransformationComponent.CODEC.optionalFieldOf("transform", TransformationComponent.DEFAULT)
                    .forGetter(BlockPart::transform)
    ).apply(instance, BlockPart::new));

    public BlockPart(BlockState state) {
        this(state, TransformationComponent.DEFAULT);
    }

    /** The block's generated geometry when it has some, its outline otherwise. */
    @Override
    public List<PlacedGeometry> geometry() {
        Matrix4f matrix = matrix();
        List<PlacedGeometry> generated = BlockShapes.geometry(this.state);
        if (generated != null) return generated.stream().map(geometry -> geometry.placed(matrix)).toList();
        return List.of(new PlacedGeometry(ShapeGeometry.of(blockOutline()), matrix));
    }

    /**
     * From the block's generated collision models at their resolution when the block collides as
     * generated, from its collision shape otherwise.
     */
    @Override
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

    private VoxelShape blockOutline() {
        return this.state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
    }
}
