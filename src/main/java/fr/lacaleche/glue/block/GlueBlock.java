package fr.lacaleche.glue.block;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.shaper.BlockShapes;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Opts a block into Glue's block extensions: its outline renderer, and, when it has geometry, an
 * outline drawn on its model at any angle, ray casts against the model rather than its voxel shape,
 * and break particles sized to the model instead of one burst per box.
 */
public interface GlueBlock {

    default ResourceLocation getOutlineRenderer() {
        return Glue.id("base");
    }

    /**
     * What the block looks like at a position: the models it draws, each where it stands, in block
     * units relative to the position.
     *
     * <p>Defaults to the geometry {@link fr.lacaleche.glue.shaper.BlockShapeProvider} generated for
     * the state. Override it for geometry that depends on more than the state, such as a block
     * entity's content. Return {@code null} to keep vanilla's voxel shapes. Called on both sides,
     * from the thread that owns {@code level}.</p>
     */
    default @Nullable List<PlacedGeometry> getGeometry(BlockState state, BlockGetter level, BlockPos pos) {
        return BlockShapes.geometry(state);
    }
}
