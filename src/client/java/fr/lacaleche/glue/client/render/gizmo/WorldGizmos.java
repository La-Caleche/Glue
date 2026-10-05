package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The gizmos drawn in the world, after everything else, at the targets' positions. Where blocks hide a
 * gizmo, it shows dimmed through them. Under an Iris shader pack, whose depth the world pass cannot rely
 * on, a gizmo is drawn over everything. Client thread only.
 */
public final class WorldGizmos {

    private static final float HIDDEN_ALPHA = 0.3F;
    private static final RenderType HIDDEN = type("hidden", DepthTestFunction.NO_DEPTH_TEST);
    private static final RenderType VISIBLE = type("visible", DepthTestFunction.LEQUAL_DEPTH_TEST);
    private static final List<Gizmo> SHOWN = new ArrayList<>();
    private static boolean registered;

    private WorldGizmos() {
    }

    /** Draws a gizmo in the world until it is hidden. Showing it twice draws it once. */
    public static void show(Gizmo gizmo) {
        if (!registered) {
            WorldRenderEvents.LAST.register(context -> render());
            registered = true;
        }
        if (!SHOWN.contains(gizmo)) SHOWN.add(gizmo);
    }

    public static void hide(Gizmo gizmo) {
        SHOWN.remove(gizmo);
    }

    public static boolean isShown(Gizmo gizmo) {
        return SHOWN.contains(gizmo);
    }

    private static void render() {
        if (SHOWN.isEmpty() || RenderCompat.isRenderingShadowPass()) return;

        GizmoView view = GizmoView.world();
        Matrix4f frameView = FrameMatrices.getView();
        if (view == null || frameView == null) return;

        List<GizmoMesh> meshes = new ArrayList<>();
        for (Gizmo gizmo : SHOWN) {
            GizmoMesh mesh = gizmo.mesh(view);
            if (mesh != null && mesh.size() > 0) meshes.add(mesh);
        }
        if (meshes.isEmpty()) return;

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(frameView);
        try {
            RenderCompat.withIrisBypass(() -> draw(meshes));
        } finally {
            modelView.popMatrix();
        }
    }

    private static void draw(List<GizmoMesh> meshes) {
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        boolean overEverything = RenderCompat.isIrisShaderEnabled();
        for (GizmoMesh mesh : meshes) {
            int[] order = mesh.backToFront();
            if (overEverything) {
                mesh.emit(buffers.getBuffer(HIDDEN), order, 1.0F);
                buffers.endBatch(HIDDEN);
            } else {
                mesh.emit(buffers.getBuffer(HIDDEN), order, HIDDEN_ALPHA);
                buffers.endBatch(HIDDEN);
                mesh.emit(buffers.getBuffer(VISIBLE), order, 1.0F);
                buffers.endBatch(VISIBLE);
            }
        }
    }

    private static RenderType type(String name, DepthTestFunction depthTest) {
        RenderPipeline pipeline = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(ResourceLocation.fromNamespaceAndPath("glue", "pipeline/gizmo_world_" + name))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.TRIANGLES)
                .withCull(false)
                .withDepthWrite(false)
                .withDepthTestFunction(depthTest)
                .build());
        RenderCompat.assignIrisProgram(pipeline, "BASIC");
        return RenderType.create("glue_gizmo_" + name, 262144, false, false, pipeline,
                RenderType.CompositeState.builder().createCompositeState(false));
    }
}
