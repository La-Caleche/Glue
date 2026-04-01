package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * Fluent builder for rendering quads with raw OpenGL, bypassing MC's pipeline and Iris hooks.
 * Draws are dispatched through {@link DeferredDrawQueue} for Iris compatibility.
 */
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

    private ShaderRenderer() {
        this.matrix = new Matrix4f().identity();
    }

    public static ShaderRenderer world() {
        return new ShaderRenderer();
    }

    public ShaderRenderer matrix(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    public ShaderRenderer position(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public ShaderRenderer size(float width, float height) {
        this.width = width;
        this.height = height;
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

    public void draw() {
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

    /**
     * API compatibility with BlockEntityRenderer.render() — bufferSource is ignored.
     */
    public void draw(MultiBufferSource bufferSource) {
        this.draw();
    }
}
