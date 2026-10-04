package fr.lacaleche.composite.client;

import fr.lacaleche.composite.BlockPart;
import fr.lacaleche.glue.client.render.model.MatrixQuadTransform;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.BlockAndTintGetter;
import org.jetbrains.annotations.Nullable;

/**
 * Moves a part's quads into place in its cell through a {@link MatrixQuadTransform}, each quad into
 * the render layer of the part's own block, and its tint into its vertex colours: the composite
 * block's layer and colours say nothing about what it holds.
 */
final class CompositePartTransform implements QuadTransform {

    private final BlockPart part;
    private final BlockAndTintGetter blockView;
    private final BlockPos pos;
    private final ChunkSectionLayer layer;
    private final @Nullable MatrixQuadTransform move;
    private final Int2IntMap tints = new Int2IntOpenHashMap();

    CompositePartTransform(BlockPart part, boolean identity, BlockAndTintGetter blockView, BlockPos pos) {
        this.part = part;
        this.blockView = blockView;
        this.pos = pos;
        this.layer = ItemBlockRenderTypes.getChunkRenderType(part.state());
        this.move = identity ? null : new MatrixQuadTransform(part.matrix());
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        quad.renderLayer(this.layer);
        int tintIndex = quad.tintIndex();
        if (tintIndex != -1) {
            int tint = this.tints.computeIfAbsent(tintIndex, this::tint);
            for (int vertex = 0; vertex < 4; vertex++) quad.color(vertex, ARGB.multiply(quad.color(vertex), tint));
            quad.tintIndex(-1);
        }
        return this.move == null || this.move.transform(quad);
    }

    private int tint(int tintIndex) {
        int color = Minecraft.getInstance().getBlockColors().getColor(this.part.state(), this.blockView, this.pos, tintIndex);
        return ARGB.opaque(color);
    }
}
