package fr.lacaleche.glue.client.render.model;

import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadTransform;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Moves block model quads by a matrix in block units: positions through the matrix, normals through
 * its normal matrix, and the face each quad is lit and sorted as to the side its turned normal points
 * to the most. A moved quad no longer lies on its block's face, so it is never culled.
 *
 * <p>Holds scratch state: use one instance per thread, such as one per {@code emitQuads} call.</p>
 */
public final class MatrixQuadTransform implements QuadTransform {

    private final Matrix4f matrix;
    private final Matrix3f normalMatrix;
    private final Vector3f scratch = new Vector3f();

    public MatrixQuadTransform(Matrix4fc matrix) {
        this.matrix = new Matrix4f(matrix);
        this.normalMatrix = this.matrix.normal(new Matrix3f());
    }

    @Override
    public boolean transform(MutableQuadView quad) {
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
