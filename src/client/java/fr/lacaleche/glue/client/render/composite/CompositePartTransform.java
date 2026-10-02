package fr.lacaleche.glue.client.render.composite;

import fr.lacaleche.glue.composite.CompositePart;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Moves a part's quads into place in its cell: positions through the part's transform, normals
 * through its normal matrix, and each quad into the render layer of the part's own block, since the
 * composite block's layer says nothing about what it holds.
 */
final class CompositePartTransform implements QuadTransform {

    private final ChunkSectionLayer layer;
    private final boolean identity;
    private final Matrix4f matrix;
    private final Matrix3f normalMatrix;
    private final Vector3f scratch = new Vector3f();

    CompositePartTransform(CompositePart part, boolean identity) {
        this.layer = ItemBlockRenderTypes.getChunkRenderType(part.state());
        this.identity = identity;
        this.matrix = part.matrix();
        this.normalMatrix = this.matrix.normal(new Matrix3f());
    }

    @Override
    public boolean transform(MutableQuadView quad) {
        quad.renderLayer(this.layer);
        if (this.identity) return true;

        for (int vertex = 0; vertex < 4; vertex++) {
            this.matrix.transformPosition(quad.x(vertex), quad.y(vertex), quad.z(vertex), this.scratch);
            quad.pos(vertex, this.scratch);
            if (quad.hasNormal(vertex)) {
                this.normalMatrix.transform(quad.normalX(vertex), quad.normalY(vertex), quad.normalZ(vertex), this.scratch);
                quad.normal(vertex, this.scratch.normalize());
            }
        }
        Vector3fc faceNormal = quad.faceNormal();
        quad.cullFace(null);
        quad.nominalFace(Direction.getApproximateNearest(faceNormal.x(), faceNormal.y(), faceNormal.z()));
        return true;
    }
}
