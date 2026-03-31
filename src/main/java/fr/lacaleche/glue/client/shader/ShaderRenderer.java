package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * A fluent builder for rendering quads with custom shaders in world contexts.
 * <p>
 * Uses raw OpenGL rendering via {@link GlDirectRenderer} to bypass MC's pipeline system
 * and all Iris/Sodium hooks. Draw commands are deferred via {@link DeferredDrawQueue}
 * to render after all world passes (including clouds).
 *
 * <h3>World Usage:</h3>
 * <pre>{@code
 * ShaderRenderer.world()
 *     .matrix(poseStack.last().pose())
 *     .position(0, 1, 0)
 *     .size(1, 1)
 *     .color(0f, 1f, 0.5f, 0.8f)
 *     .draw(bufferSource);
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class ShaderRenderer {

    private Matrix4f matrix;
    private float x, y, z;
    private float width, height;
    private boolean centered = false;

    // Per-vertex colors (default white)
    private float r1 = 1f, g1 = 1f, b1 = 1f, a1 = 1f; // top-left
    private float r2 = 1f, g2 = 1f, b2 = 1f, a2 = 1f; // top-right
    private float r3 = 1f, g3 = 1f, b3 = 1f, a3 = 1f; // bottom-right
    private float r4 = 1f, g4 = 1f, b4 = 1f, a4 = 1f; // bottom-left

    private ShaderRenderer() {
        this.matrix = new Matrix4f().identity();
    }

    /**
     * Creates a ShaderRenderer for world/3D rendering.
     */
    public static ShaderRenderer world() {
        return new ShaderRenderer();
    }

    /**
     * Sets the transformation matrix (from PoseStack).
     */
    public ShaderRenderer matrix(Matrix4f matrix) {
        this.matrix = matrix;
        return this;
    }

    /**
     * Sets the position of the quad.
     */
    public ShaderRenderer position(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    /**
     * Sets the size of the quad.
     */
    public ShaderRenderer size(float width, float height) {
        this.width = width;
        this.height = height;
        return this;
    }

    /**
     * If true, the quad is centered around the position.
     */
    public ShaderRenderer centered(boolean centered) {
        this.centered = centered;
        return this;
    }

    /**
     * Sets uniform color for all vertices.
     */
    public ShaderRenderer color(float r, float g, float b, float a) {
        this.r1 = r; this.g1 = g; this.b1 = b; this.a1 = a;
        this.r2 = r; this.g2 = g; this.b2 = b; this.a2 = a;
        this.r3 = r; this.g3 = g; this.b3 = b; this.a3 = a;
        this.r4 = r; this.g4 = g; this.b4 = b; this.a4 = a;
        return this;
    }

    /**
     * Sets per-corner colors for gradient effects.
     * Order: top-left, top-right, bottom-right, bottom-left.
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

    /**
     * Enqueues the quad for deferred rendering using raw OpenGL.
     * <p>
     * Bypasses MC's entire pipeline system ({@code GlDevice}, {@code GlRenderPass},
     * {@code GlCommandEncoder}) and all Iris hooks. Computes the full MVP matrix
     * on the CPU and defers the draw to after all world passes (including clouds).
     */
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
                x0, y1, this.z,  // bottom-left
                x1, y1, this.z,  // bottom-right
                x1, y0, this.z,  // top-right
                x0, y0, this.z,  // top-left
        };

        float[] colors = {
                this.r4, this.g4, this.b4, this.a4,  // bottom-left
                this.r3, this.g3, this.b3, this.a3,  // bottom-right
                this.r2, this.g2, this.b2, this.a2,  // top-right
                this.r1, this.g1, this.b1, this.a1,  // top-left
        };

        // Build MVP = Projection * ViewMatrix * PoseStack
        // - Projection: from GameRenderer (effective FOV + view bob)
        // - ViewMatrix: camera rotation from RenderSystem.getModelViewMatrix()
        // - PoseStack: this.matrix (block position offset + local transforms)
        Matrix4f projMat = GlDirectRenderer.getProjectionMatrix();
        Matrix4f viewMat = RenderSystem.getModelViewMatrix();
        Matrix4f mvp = new Matrix4f(projMat).mul(viewMat).mul(this.matrix);

        DeferredDrawQueue.enqueue(mvp, vertices, colors, 4);
    }

    /**
     * Enqueues the quad for deferred rendering, ignoring the provided buffer source.
     * <p>
     * The buffer source parameter exists for API compatibility with
     * {@code BlockEntityRenderer.render()} but is not used — raw GL rendering
     * bypasses MC's buffer system entirely.
     *
     * @param bufferSource Ignored — exists for API compatibility
     */
    public void draw(MultiBufferSource bufferSource) {
        this.draw();
    }
}
