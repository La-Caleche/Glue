package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

/**
 * A custom GuiElementRenderState for rendering colored quads (and degenerate quads for triangles)
 * through the modern Minecraft GUI rendering pipeline.
 * <p>
 * Uses a custom no-cull pipeline derived from {@link RenderPipelines#GUI_SNIPPET} to ensure
 * all gizmo faces render regardless of winding order (since they come from 3D→2D projection).
 */
public class GizmoQuadRenderState implements GuiElementRenderState {

    /**
     * A GUI pipeline with backface culling disabled.
     * Required because gizmo quads have arbitrary winding order from 3D→2D projection.
     */
    public static final RenderPipeline GUI_NO_CULL = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                    .withLocation("pipeline/glue_gizmo_gui_no_cull")
                    .withCull(false)
                    .build()
    );

    private final Matrix3x2f pose;
    private final float x1, y1, x2, y2, x3, y3, x4, y4;
    private final int color;
    private final ScreenRectangle bounds;

    public GizmoQuadRenderState(Matrix3x2f pose,
                                float x1, float y1,
                                float x2, float y2,
                                float x3, float y3,
                                float x4, float y4,
                                int color) {
        this.pose = pose;
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.x3 = x3;
        this.y3 = y3;
        this.x4 = x4;
        this.y4 = y4;
        this.color = color;

        // Compute axis-aligned bounding box for the quad
        float minX = Math.min(Math.min(x1, x2), Math.min(x3, x4));
        float minY = Math.min(Math.min(y1, y2), Math.min(y3, y4));
        float maxX = Math.max(Math.max(x1, x2), Math.max(x3, x4));
        float maxY = Math.max(Math.max(y1, y2), Math.max(y3, y4));
        this.bounds = new ScreenRectangle((int) minX, (int) minY,
                (int) (maxX - minX) + 1, (int) (maxY - minY) + 1);
    }

    @Override
    public void buildVertices(VertexConsumer vertexConsumer, float z) {
        vertexConsumer.addVertexWith2DPose(pose, x1, y1, z).setColor(color);
        vertexConsumer.addVertexWith2DPose(pose, x2, y2, z).setColor(color);
        vertexConsumer.addVertexWith2DPose(pose, x3, y3, z).setColor(color);
        vertexConsumer.addVertexWith2DPose(pose, x4, y4, z).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return GUI_NO_CULL;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    @Nullable
    public ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    @Nullable
    public ScreenRectangle bounds() {
        return bounds;
    }
}
