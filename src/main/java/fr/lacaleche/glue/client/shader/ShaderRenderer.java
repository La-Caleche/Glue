package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.function.BiConsumer;

@Environment(EnvType.CLIENT)
public class ShaderRenderer {

    private Matrix4f matrix;
    private float x, y, z;
    private float width, height;
    private boolean centered = false;

    private float r1 = 1f, g1 = 1f, b1 = 1f, a1 = 1f;
    private float r2 = 1f, g2 = 1f, b2 = 1f, a2 = 1f;
    private float r3 = 1f, g3 = 1f, b3 = 1f, a3 = 1f;
    private float r4 = 1f, g4 = 1f, b4 = 1f, a4 = 1f;

    private int captureTextureId = -1;

    private static RenderTarget tempFbo;

    private ShaderRenderer() {
        this.matrix = new Matrix4f().identity();
    }

    public static ShaderRenderer world() {
        return new ShaderRenderer();
    }

    public static void defer(Runnable action) {
        if (RenderCompat.isRenderingShadowPass()) return;
        DeferredDrawQueue.defer(action);
    }

    public ShaderRenderer matrix(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    public ShaderRenderer position(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        return this;
    }

    public ShaderRenderer size(float width, float height) {
        this.width = width; this.height = height;
        return this;
    }

    public ShaderRenderer centered(boolean centered) {
        this.centered = centered;
        return this;
    }

    public ShaderRenderer color(float r, float g, float b, float a) {
        this.r1 = r; this.g1 = g; this.b1 = b; this.a1 = a;
        this.r2 = r; this.g2 = g; this.b2 = b; this.a2 = a;
        this.r3 = r; this.g3 = g; this.b3 = b; this.a3 = a;
        this.r4 = r; this.g4 = g; this.b4 = b; this.a4 = a;
        return this;
    }

    public ShaderRenderer cornerColors(
            float r1, float g1, float b1, float a1,
            float r2, float g2, float b2, float a2,
            float r3, float g3, float b3, float a3,
            float r4, float g4, float b4, float a4) {
        this.r1 = r1; this.g1 = g1; this.b1 = b1; this.a1 = a1;
        this.r2 = r2; this.g2 = g2; this.b2 = b2; this.a2 = a2;
        this.r3 = r3; this.g3 = g3; this.b3 = b3; this.a3 = a3;
        this.r4 = r4; this.g4 = g4; this.b4 = b4; this.a4 = a4;
        return this;
    }

    public ShaderRenderer capture(int fboWidth, int fboHeight,
                                  BiConsumer<PoseStack, MultiBufferSource> renderer) {
        tempFbo = FramebufferHelper.resizeOrCreate(tempFbo, fboWidth, fboHeight);
        FramebufferHelper.clear(tempFbo, 0f, 0f, 0f, 0f);

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        int fboId = FramebufferHelper.getFramebufferId(tempFbo);
        if (fboId < 0) return this;
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboId);
        GL11.glViewport(0, 0, fboWidth, fboHeight);

        PoseStack captureStack = new PoseStack();
        ByteBufferBuilder byteBuffer = new ByteBufferBuilder(256);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

        renderer.accept(captureStack, bufferSource);
        bufferSource.endBatch();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

        this.captureTextureId = FramebufferHelper.getColorTextureId(tempFbo);
        return this;
    }

    public void draw() {
        if (captureTextureId >= 0) {
            drawTextured();
        } else {
            drawColored();
        }
    }

    public void draw(MultiBufferSource bufferSource) {
        this.draw();
    }

    private void drawColored() {
        float x0, y0, x1, y1;
        if (this.centered) {
            x0 = this.x - this.width / 2f;
            y0 = this.y - this.height / 2f;
            x1 = this.x + this.width / 2f;
            y1 = this.y + this.height / 2f;
        } else {
            x0 = this.x;
            y0 = this.y;
            x1 = this.x + this.width;
            y1 = this.y + this.height;
        }

        float[] vertices = {
                x0, y1, this.z,
                x1, y1, this.z,
                x1, y0, this.z,
                x0, y0, this.z,
        };

        float[] colors = {
                this.r4, this.g4, this.b4, this.a4,
                this.r3, this.g3, this.b3, this.a3,
                this.r2, this.g2, this.b2, this.a2,
                this.r1, this.g1, this.b1, this.a1,
        };

        Matrix4f projMat = GlDirectRenderer.getProjectionMatrix();
        Matrix4f viewMat = RenderSystem.getModelViewMatrix();
        Matrix4f mvp = new Matrix4f(projMat).mul(viewMat).mul(this.matrix);

        DeferredDrawQueue.enqueue(mvp, vertices, colors, 4);
    }

    private void drawTextured() {
        float x0, y0, x1, y1;
        if (this.centered) {
            x0 = this.x - this.width / 2f;
            y0 = this.y - this.height / 2f;
            x1 = this.x + this.width / 2f;
            y1 = this.y + this.height / 2f;
        } else {
            x0 = this.x;
            y0 = this.y;
            x1 = this.x + this.width;
            y1 = this.y + this.height;
        }

        float[] vertices = {
                x0, y1, this.z,
                x1, y1, this.z,
                x1, y0, this.z,
                x0, y0, this.z,
        };

        float[] uvs = {
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f,
        };

        float[] colors = {
                this.r4, this.g4, this.b4, this.a4,
                this.r3, this.g3, this.b3, this.a3,
                this.r2, this.g2, this.b2, this.a2,
                this.r1, this.g1, this.b1, this.a1,
        };

        Matrix4f projMat = GlDirectRenderer.getProjectionMatrix();
        Matrix4f viewMat = RenderSystem.getModelViewMatrix();
        Matrix4f mvp = new Matrix4f(projMat).mul(viewMat).mul(this.matrix);

        DeferredDrawQueue.enqueueTextured(mvp, vertices, uvs, colors, captureTextureId, 4);
    }
}
