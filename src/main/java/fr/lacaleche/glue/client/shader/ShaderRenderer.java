package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

/**
 * A fluent builder for rendering quads with custom shaders in both GUI and world contexts.
 * <p>
 * This replaces FDLib's {@code FDShaderRenderer} for the MC 1.21.8 pipeline API.
 * Instead of using {@code ShaderInstance} + {@code BufferUploader.drawWithShader()}, it uses
 * {@code RenderType} + {@code MultiBufferSource} with custom {@code RenderPipeline} objects.
 *
 * <h3>GUI Usage:</h3>
 * <pre>{@code
 * ShaderRenderer.gui(pipeline)
 *     .matrix(guiGraphics.pose().last().pose())
 *     .position(10, 10, 0)
 *     .size(100, 100)
 *     .color(1f, 0f, 0f, 1f)
 *     .draw();
 * }</pre>
 *
 * <h3>World Usage:</h3>
 * <pre>{@code
 * ShaderRenderer.world(pipeline)
 *     .matrix(poseStack.last().pose())
 *     .position(0, 1, 0)
 *     .size(1, 1)
 *     .color(0f, 1f, 0.5f, 0.8f)
 *     .draw(bufferSource);
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class ShaderRenderer {

    /**
     * The format type determines how vertex data is composed.
     */
    public enum Format {
        /** Position + Texture UV + Color (default) */
        POSITION_TEX_COLOR,
        /** Position + Color only */
        POSITION_COLOR,
        /** Position only */
        POSITION
    }

    private final RenderPipeline pipeline;
    private final RenderType.CompositeState compositeState;

    private Matrix4f matrix;
    private float x, y, z;
    private float width, height;
    private boolean centered = false;

    // Per-vertex colors (default white)
    private float r1 = 1f, g1 = 1f, b1 = 1f, a1 = 1f; // top-left
    private float r2 = 1f, g2 = 1f, b2 = 1f, a2 = 1f; // top-right
    private float r3 = 1f, g3 = 1f, b3 = 1f, a3 = 1f; // bottom-right
    private float r4 = 1f, g4 = 1f, b4 = 1f, a4 = 1f; // bottom-left

    // UV span
    private float uSpan = 1f, vSpan = 1f;

    private Format format;

    private ShaderRenderer(RenderPipeline pipeline, RenderType.CompositeState compositeState, Format format) {
        this.pipeline = pipeline;
        this.compositeState = compositeState;
        this.format = format;
        this.matrix = new Matrix4f().identity();
    }

    /**
     * Creates a ShaderRenderer for GUI/screen rendering with POSITION_TEX_COLOR format.
     */
    public static ShaderRenderer gui(RenderPipeline pipeline) {
        return gui(pipeline, RenderType.CompositeState.builder().createCompositeState(false));
    }

    /**
     * Creates a ShaderRenderer for GUI/screen rendering with custom composite state.
     */
    public static ShaderRenderer gui(RenderPipeline pipeline, RenderType.CompositeState state) {
        return new ShaderRenderer(pipeline, state, Format.POSITION_TEX_COLOR);
    }

    /**
     * Creates a ShaderRenderer for world/3D rendering with POSITION_TEX_COLOR format.
     */
    public static ShaderRenderer world(RenderPipeline pipeline) {
        return world(pipeline, RenderType.CompositeState.builder().createCompositeState(false));
    }

    /**
     * Creates a ShaderRenderer for world/3D rendering with custom composite state.
     */
    public static ShaderRenderer world(RenderPipeline pipeline, RenderType.CompositeState state) {
        return new ShaderRenderer(pipeline, state, Format.POSITION_TEX_COLOR);
    }

    /**
     * Sets the vertex format type.
     */
    public ShaderRenderer format(Format format) {
        this.format = format;
        return this;
    }

    /**
     * Sets the transformation matrix (from PoseStack or GuiGraphics).
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
     * Sets the UV span for texture coordinates.
     */
    public ShaderRenderer uvSpan(float uSpan, float vSpan) {
        this.uSpan = uSpan;
        this.vSpan = vSpan;
        return this;
    }

    /**
     * Draws the quad immediately using raw OpenGL.
     * <p>
     * Bypasses MC's entire pipeline system ({@code GlDevice}, {@code GlRenderPass},
     * {@code GlCommandEncoder}) and all Iris hooks. Computes the full MVP matrix
     * on the CPU and renders with a self-compiled GL shader program.
     */
    public void draw() {
        // Compute quad corners
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

        // Position data (local space, will be transformed by MVP in shader)
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

        // Enqueue for deferred rendering after all world passes (including clouds)
        DeferredDrawQueue.enqueue(mvp, vertices, colors, 4);
    }

    /**
     * Draws the quad immediately, ignoring the provided buffer source.
     * <p>
     * Custom shader pipelines cannot use Iris's batched buffer source because
     * Iris flushes it later outside the bypass window. This method delegates
     * to {@link #draw()} which renders directly.
     *
     * @param bufferSource Ignored — exists for API compatibility with {@code BlockEntityRenderer.render()}
     */
    public void draw(MultiBufferSource bufferSource) {
        this.draw();
    }


    private void emitVertices(VertexConsumer consumer) {
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

        switch (this.format) {
            case POSITION_TEX_COLOR -> {
                // bottom-left
                consumer.addVertex(this.matrix, x0, y1, this.z).setUv(0, this.vSpan).setColor(this.r4, this.g4, this.b4, this.a4);
                // bottom-right
                consumer.addVertex(this.matrix, x1, y1, this.z).setUv(this.uSpan, this.vSpan).setColor(this.r3, this.g3, this.b3, this.a3);
                // top-right
                consumer.addVertex(this.matrix, x1, y0, this.z).setUv(this.uSpan, 0).setColor(this.r2, this.g2, this.b2, this.a2);
                // top-left
                consumer.addVertex(this.matrix, x0, y0, this.z).setUv(0, 0).setColor(this.r1, this.g1, this.b1, this.a1);
            }
            case POSITION_COLOR -> {
                consumer.addVertex(this.matrix, x0, y1, this.z).setColor(this.r4, this.g4, this.b4, this.a4);
                consumer.addVertex(this.matrix, x1, y1, this.z).setColor(this.r3, this.g3, this.b3, this.a3);
                consumer.addVertex(this.matrix, x1, y0, this.z).setColor(this.r2, this.g2, this.b2, this.a2);
                consumer.addVertex(this.matrix, x0, y0, this.z).setColor(this.r1, this.g1, this.b1, this.a1);
            }
            case POSITION -> {
                consumer.addVertex(this.matrix, x0, y1, this.z);
                consumer.addVertex(this.matrix, x1, y1, this.z);
                consumer.addVertex(this.matrix, x1, y0, this.z);
                consumer.addVertex(this.matrix, x0, y0, this.z);
            }
        }
    }
}
