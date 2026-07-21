package fr.lacaleche.glue.client.transform;

import com.mojang.blaze3d.vertex.PoseStack;
import org.jetbrains.annotations.ApiStatus;
import org.joml.Matrix3fc;
import org.joml.Matrix4fc;
import org.joml.Quaternionfc;

public final class PoseStackTransform implements GlueTransformStack {

    private final PoseStack stack;

    @ApiStatus.Internal
    public PoseStackTransform(PoseStack stack) {
        this.stack = stack;
    }

    @Override
    public GlueTransformStack rotate(Quaternionfc quaternion) {
        stack.mulPose(quaternion);
        return this;
    }

    @Override
    public GlueTransformStack scale(float factorX, float factorY, float factorZ) {
        stack.scale(factorX, factorY, factorZ);
        return this;
    }

    @Override
    public GlueTransformStack mulPose(Matrix4fc pose) {
        stack.last()
                .pose()
                .mul(pose);
        return this;
    }

    @Override
    public GlueTransformStack mulNormal(Matrix3fc normal) {
        stack.last()
                .normal()
                .mul(normal);
        return this;
    }

    @Override
    public GlueTransformStack translate(float x, float y, float z) {
        stack.translate(x, y, z);
        return this;
    }

    @Override
    public GlueTransformStack translate(double x, double y, double z) {
        stack.translate(x, y, z);
        return this;
    }

    @Override
    public GlueTransformStack pushPose() {
        stack.pushPose();
        return this;
    }

    @Override
    public GlueTransformStack popPose() {
        stack.popPose();
        return this;
    }
}
