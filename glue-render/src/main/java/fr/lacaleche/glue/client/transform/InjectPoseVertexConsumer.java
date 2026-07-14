package fr.lacaleche.glue.client.transform;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A {@link VertexConsumer} wrapper that pre-multiplies every vertex by a captured
 * {@link PoseStack.Pose}. Use this when you have a {@code PoseStack} pose you
 * want to apply to vertices outside the usual MC render flow.
 */
public class InjectPoseVertexConsumer implements VertexConsumer {
    private final Matrix4f pose;
    private final Matrix3f normal;
    private final VertexConsumer delegate;

    public InjectPoseVertexConsumer(PoseStack poseStack, VertexConsumer delegate) {
        this.pose = poseStack.last().pose();
        this.normal = poseStack.last().normal();
        this.delegate = delegate;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        Vector4f vector4f = this.pose.transform(new Vector4f(x, y, z, 1.0F));
        return this.delegate.addVertex(vector4f.x, vector4f.y, vector4f.z);
    }

    /**
     * The caller-supplied {@code matrix} is intentionally ignored — this wrapper
     * always applies the pose captured at construction time. Use the no-matrix
     * overload to make the intent obvious at the call site.
     */
    @Override
    public VertexConsumer addVertex(Matrix4f matrix, float x, float y, float z) {
        Vector4f vector4f = this.pose.transform(new Vector4f(x, y, z, 1.0F));
        return this.delegate.addVertex(vector4f.x, vector4f.y, vector4f.z);
    }

    @Override
    public VertexConsumer setColor(int red, int green, int blue, int alpha) {
        return this.delegate.setColor(red, green, blue, alpha);
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this.delegate.setUv(u, v);
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this.delegate.setUv1(u, v);
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this.delegate.setUv2(u, v);
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        Vector3f vector3f = this.normal.transform(new Vector3f(x, y, z));
        return this.delegate.setNormal(vector3f.x, vector3f.y, vector3f.z);
    }
}
