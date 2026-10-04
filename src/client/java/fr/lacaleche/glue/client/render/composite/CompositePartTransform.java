package fr.lacaleche.glue.client.render.composite;

import fr.lacaleche.glue.client.render.model.MatrixQuadTransform;
import fr.lacaleche.glue.composite.CompositePart;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a part's quads into place in its cell through a {@link MatrixQuadTransform}, and each quad
 * into the render layer of the part's own block, since the composite block's layer says nothing
 * about what it holds.
 */
final class CompositePartTransform implements QuadTransform {

    private final ChunkSectionLayer layer;
    private final @Nullable MatrixQuadTransform move;

    CompositePartTransform(CompositePart part, boolean identity) {
        this.layer = ItemBlockRenderTypes.getChunkRenderType(part.state());
        this.move = identity ? null : new MatrixQuadTransform(part.matrix());
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        quad.renderLayer(this.layer);
        return this.move == null || this.move.transform(quad);
    }
}
