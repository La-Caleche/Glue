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

import java.util.function.BiConsumer;

/**
 * Fluent builder for rendering content with raw OpenGL, bypassing MC's pipeline and Iris hooks.
 * Draws are dispatched through {@link DeferredDrawQueue} for Iris compatibility.
 *
 * <p><b>Two rendering modes:</b></p>
 * <ul>
 *   <li><b>Colored quad</b> — flat or gradient colors via {@link #color}/{@link #cornerColors} + {@link #draw()}</li>
 *   <li><b>FBO capture</b> — render any MC content via {@link #capture(int, int, BiConsumer)} + {@link #draw()}.
 *       The content is rendered to a temporary FBO, then blitted as a textured quad through the deferred pipeline.</li>
 * </ul>
 *
 * <p><b>Usage examples:</b></p>
 * <pre>{@code
 * // Colored quad (existing API)
 * ShaderRenderer.world()
 *     .matrix(poseStack.last().pose())
 *     .position(-0.5f, -0.5f, 0)
 *     .size(1f, 1f)
 *     .color(1, 0, 0, 0.8f)
 *     .draw();
 *
 * // FBO capture — render an item through the deferred pipeline
 * ShaderRenderer.world()
 *     .matrix(poseStack.last().pose())
 *     .position(-0.5f, -0.5f, 0)
 *     .size(1f, 1f)
 *     .capture(128, 128, (captureStack, bufferSource) -> {
 *         itemRenderer.renderStatic(stack, context, light, overlay,
 *                 captureStack, bufferSource, level, 0);
 *     })
 *     .draw();
 *
 * // Defer arbitrary rendering for Iris compatibility
 * ShaderRenderer.defer(() -> {
 *     itemRenderer.renderStatic(stack, ctx, light, overlay, matrices, bufferSource, level, 0);
 * });
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class ShaderRenderer {

    private Matrix4f matrix;
    private float x, y, z;
    private float width, height;
    private boolean centered = false;

    // Colored quad state
    private float r1 = 1f, g1 = 1f, b1 = 1f, a1 = 1f;
    private float r2 = 1f, g2 = 1f, b2 = 1f, a2 = 1f;
    private float r3 = 1f, g3 = 1f, b3 = 1f, a3 = 1f;
    private float r4 = 1f, g4 = 1f, b4 = 1f, a4 = 1f;

    // FBO capture state
    private int captureTextureId = -1;

    // Reusable temp FBO (shared across all ShaderRenderer instances per frame)
    private static RenderTarget tempFbo;

    private ShaderRenderer() {
        this.matrix = new Matrix4f().identity();
    }

    public static ShaderRenderer world() {
        return new ShaderRenderer();
    }

    /**
     * Defers an arbitrary rendering action for Iris compatibility.
     * If Iris is not active, the action executes immediately.
     */
    public static void defer(Runnable action) {
        if (RenderCompat.isRenderingShadowPass()) return;
        DeferredDrawQueue.defer(action);
    }

    // ── Transform ────────────────────────────────────────────────

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

    // ── Color mode ───────────────────────────────────────────────

    public ShaderRenderer color(float r, float g, float b, float a) {
        this.r1 = r; this.g1 = g; this.b1 = b; this.a1 = a;
        this.r2 = r; this.g2 = g; this.b2 = b; this.a2 = a;
        this.r3 = r; this.g3 = g; this.b3 = b; this.a3 = a;
        this.r4 = r; this.g4 = g; this.b4 = b; this.a4 = a;
        return this;
    }

    /**
     * Sets per-corner colors (top-left, top-right, bottom-right, bottom-left).
     */
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

    // ── FBO Capture mode ─────────────────────────────────────────

    /**
     * Captures arbitrary MC rendering to a temporary FBO.
     * The captured texture will be drawn as a textured quad when {@link #draw()} is called.
     *
     * <p>The {@code renderer} receives a fresh {@link PoseStack} (identity) and a
     * {@link MultiBufferSource.BufferSource} that renders to the temp FBO.
     * The FBO is cleared to transparent black before each capture.</p>
     *
     * @param fboWidth  width of the capture FBO in pixels
     * @param fboHeight height of the capture FBO in pixels
     * @param renderer  the rendering lambda — render anything using MC's pipeline
     */
    public ShaderRenderer capture(int fboWidth, int fboHeight,
                                  BiConsumer<PoseStack, MultiBufferSource> renderer) {
        tempFbo = FramebufferHelper.resizeOrCreate(tempFbo, fboWidth, fboHeight);
        FramebufferHelper.clear(tempFbo, 0f, 0f, 0f, 0f);

        // Save current GL FBO
        int prevFbo = org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        org.lwjgl.opengl.GL11.glGetIntegerv(org.lwjgl.opengl.GL11.GL_VIEWPORT, prevViewport);

        // Bind temp FBO
        int fboId = FramebufferHelper.getFramebufferId(tempFbo);
        if (fboId < 0) return this; // unsupported, skip capture
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, fboId);
        org.lwjgl.opengl.GL11.glViewport(0, 0, fboWidth, fboHeight);

        // Render using MC's pipeline into the temp FBO
        PoseStack captureStack = new PoseStack();
        ByteBufferBuilder byteBuffer = new ByteBufferBuilder(256);
        MultiBufferSource.BufferSource bufferSource = MultiBufferSource.immediate(byteBuffer);

        renderer.accept(captureStack, bufferSource);
        bufferSource.endBatch();

        // Restore previous FBO
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prevFbo);
        org.lwjgl.opengl.GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);

        this.captureTextureId = FramebufferHelper.getColorTextureId(tempFbo);
        return this;
    }

    // ── Draw ─────────────────────────────────────────────────────

    public void draw() {
        if (captureTextureId >= 0) {
            drawTextured();
        } else {
            drawColored();
        }
    }

    /**
     * API compatibility with BlockEntityRenderer.render() — bufferSource is ignored.
     */
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

        // Quad vertices (triangle fan: BL, BR, TR, TL)
        float[] vertices = {
                x0, y1, this.z,
                x1, y1, this.z,
                x1, y0, this.z,
                x0, y0, this.z,
        };

        // UVs (matching vertex order)
        float[] uvs = {
                0f, 0f,
                1f, 0f,
                1f, 1f,
                0f, 1f,
        };

        // Tint colors (white = no tint by default, or user can set color as a tint)
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
