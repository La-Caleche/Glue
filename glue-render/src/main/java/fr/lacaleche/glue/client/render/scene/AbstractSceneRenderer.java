package fr.lacaleche.glue.client.render.scene;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ARGB;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

public abstract class AbstractSceneRenderer {

    protected static final float NEAR_PLANE = 0.1f;
    protected static final float FAR_PLANE = 1000.0f;

    protected float[] clearColor = {0.1f, 0.1f, 0.15f, 1.0f};
    /**
     * Scale applied to the scene. 1.0 = 1× (identity).
     */
    protected float fov = 60.0f;

    protected RenderTarget framebuffer = null;
    protected GpuBuffer projectionBuffer;

    public float[] getClearColor() {
        return clearColor;
    }

    public void setClearColor(float[] clearColor) {
        if (clearColor.length < 3) {
            throw new IllegalArgumentException("clearColor must have at least 3 components (R, G, B)");
        }
        this.clearColor = clearColor;
    }

    public float getFov() {
        return fov;
    }

    public void setFov(float fov) {
        this.fov = fov;
    }

    public RenderTarget getFramebuffer() {
        return framebuffer;
    }

    public int renderToTexture(int width, int height, Minecraft client) {
        try {
            framebuffer = FramebufferHelper.resizeOrCreate(framebuffer, width, height);

            if (projectionBuffer == null || projectionBuffer.isClosed()) {
                projectionBuffer = RenderSystem.getDevice().createBuffer(() -> "SceneRenderer Projection",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, RenderSystem.PROJECTION_MATRIX_UBO_SIZE);
            }

            Matrix4f projection = createProjectionMatrix(width, height);
            try (GpuBuffer.MappedView view = RenderSystem.getDevice().createCommandEncoder()
                    .mapBuffer(projectionBuffer, false, true)) {
                Std140Builder.intoBuffer(view.data()).putMat4f(projection);
            }

            // The viewport is not part of RenderSystem's backup/restore, and this
            // renderer can run mid-frame (light shadow maps), so snapshot it here.
            int[] prevViewport = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

            RenderSystem.backupProjectionMatrix();
            PoseStack matrices = buildModelViewMatrix(buildViewMatrix(), getScale());
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                    framebuffer.getColorTexture(),
                    ARGB.colorFromFloat(1.0f, clearColor[0], clearColor[1], clearColor[2]),
                    framebuffer.getDepthTexture(),
                    1.0d);
            RenderSystem.setProjectionMatrix(projectionBuffer.slice(), ProjectionType.PERSPECTIVE);
            RenderSystem.getModelViewStack().pushMatrix().mul(matrices.last().pose());
            RenderSystem.outputColorTextureOverride = framebuffer.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = framebuffer.getDepthTextureView();
            GlStateManager._viewport(0, 0, width, height);
            client.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);

            try {
                renderScene(client, new PoseStack());
                renderGrid(new PoseStack());
            } finally {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
                RenderSystem.getModelViewStack().popMatrix();
                RenderSystem.restoreProjectionMatrix();
                GlStateManager._viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
            }

            return FramebufferHelper.getColorTextureId(framebuffer);

        } catch (Exception e) {
            Glue.LOGGER.error("Error rendering 3D scene", e);
            return -1;
        }
    }

    protected abstract void renderScene(Minecraft client, PoseStack matrices);

    protected abstract Matrix4f buildViewMatrix();

    protected abstract float getScale();

    protected PoseStack buildModelViewMatrix(Matrix4f viewMatrix, float scale) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        matrices.scale(scale, scale, scale);
        matrices.translate(-0.5f, -0.5f, -0.5f);
        return matrices;
    }

    protected Matrix4f createProjectionMatrix(int width, int height) {
        return new Matrix4f().setPerspective(
                (float) Math.toRadians(this.fov),
                (float) width / (float) height,
                NEAR_PLANE,
                FAR_PLANE);
    }

    protected void renderGrid(PoseStack matrices) {
        // no-op by default
    }

    public void cleanup() {
        if (framebuffer != null) {
            framebuffer.destroyBuffers();
            framebuffer = null;
        }
        if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
    }
}
