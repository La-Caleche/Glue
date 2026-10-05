package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * A gizmo's triangles projected to the screen, in the order they were sorted, as one GUI element. The GUI
 * pipeline draws quads, so each triangle repeats its last corner.
 */
final class GizmoGuiRenderState implements GuiElementRenderState {

    /** The GUI pipeline without culling: a projected triangle's winding depends on the side it is seen from. */
    private static final RenderPipeline PIPELINE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
            .withLocation(ResourceLocation.fromNamespaceAndPath("glue", "pipeline/gizmo_gui"))
            .withCull(false)
            .build());

    private final Matrix3x2f pose;
    private final float[] corners;
    private final int[] colors;
    private final ScreenRectangle bounds;

    GizmoGuiRenderState(Matrix3x2f pose, GizmoMesh mesh, GizmoView view) {
        this.pose = pose;
        int[] order = mesh.backToFront();
        float[] projected = new float[order.length * 6];
        int[] tints = new int[order.length];
        int kept = 0;
        Vector3f corner = new Vector3f();
        for (int triangle : order) {
            boolean visible = true;
            for (int i = 0; i < 3 && visible; i++) {
                mesh.corner(triangle, i, corner);
                Vector3f screen = view.project(corner);
                if (screen == null) {
                    visible = false;
                } else {
                    projected[kept * 6 + i * 2] = screen.x;
                    projected[kept * 6 + i * 2 + 1] = screen.y;
                }
            }
            if (!visible) continue;

            tints[kept] = mesh.color(triangle, 1.0F);
            kept++;
        }
        this.corners = Arrays.copyOf(projected, kept * 6);
        this.colors = Arrays.copyOf(tints, kept);
        this.bounds = new ScreenRectangle((int) view.left(), (int) view.top(), (int) Math.ceil(view.width()),
                (int) Math.ceil(view.height()));
    }

    @Override
    public void buildVertices(VertexConsumer consumer, float depth) {
        for (int triangle = 0; triangle < this.colors.length; triangle++) {
            int at = triangle * 6;
            int color = this.colors[triangle];
            consumer.addVertexWith2DPose(this.pose, this.corners[at], this.corners[at + 1], depth).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[at + 2], this.corners[at + 3], depth).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[at + 4], this.corners[at + 5], depth).setColor(color);
            consumer.addVertexWith2DPose(this.pose, this.corners[at + 4], this.corners[at + 5], depth).setColor(color);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return PIPELINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    @Override
    public @Nullable ScreenRectangle scissorArea() {
        return null;
    }

    @Override
    public ScreenRectangle bounds() {
        return this.bounds;
    }
}
