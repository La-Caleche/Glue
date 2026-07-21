package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.internal.LumosBuffers;
import fr.lacaleche.glue.client.render.light.mixin.LumosLevelRendererAccessor;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Re-renders entities near lights from the camera's point of view into the shared material G-buffer
 * under the {@code ENTITY} material id (2) &mdash; the reduced-capture stand-in for the world-phase
 * entity capture, which an Iris shaderpack frame blocks. Only runs on those frames.
 *
 * <p>Entities draw through their ordinary vanilla render types (whose patched {@code core/entity}
 * shaders write the material outputs), placed camera-relative by vanilla's own
 * {@code renderEntity}, so interpolation, pose and armour/held items all match what the pack drew.
 * The capture seam supplies the decal discipline the vanilla pipelines cannot carry themselves
 * (polygon offset + forced depth-mask-off &mdash; see {@code GBufferCapture.beginEntityRecapture}),
 * so only fragments matching the scene's own entity surface claim their pixels and the borrowed
 * scene depth is never written.</p>
 */
@Environment(EnvType.CLIENT)
public final class EntityMaterialSceneRenderer extends AbstractSceneRenderer {

    /** Swallows every non-entity-format draw an entity render emits (name tags, leashes, text
     *  shadows): with no framebuffer of our own bound, letting them flush would paint them into
     *  the main scene from this pass's viewpoint. */
    private static final VertexConsumer DISCARD = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };

    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<Entity> entities = List.of();
    private float partialTick;

    public void configure(Matrix4f view, Matrix4f proj, double camX, double camY, double camZ,
                          List<Entity> entities, float partialTick) {
        this.view = view;
        this.proj = proj;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.entities = entities;
        this.partialTick = partialTick;
    }

    @Override
    protected Matrix4f createProjectionMatrix(int width, int height) {
        return proj;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return view;
    }

    @Override
    protected float getScale() {
        return 1.0f;
    }

    @Override
    protected PoseStack buildModelViewMatrix(Matrix4f viewMatrix, float scale) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        return matrices;
    }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null || entities.isEmpty()) return;
        if (!GBufferCapture.beginEntityRecapture()) return;

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);
        LumosLevelRendererAccessor levelRenderer = (LumosLevelRendererAccessor) client.levelRenderer;
        MultiBufferSource.BufferSource buffers = LumosBuffers.source();
        MultiBufferSource filtered = type ->
                type.format() == DefaultVertexFormat.NEW_ENTITY ? buffers.getBuffer(type) : DISCARD;
        try {
            for (Entity entity : entities) {
                levelRenderer.glue$renderEntity(entity, camX, camY, camZ,
                        partialTick, matrices, filtered);
            }
            buffers.endBatch();
        } finally {
            dispatcher.setRenderShadow(true);
            GBufferCapture.endEntityRecapture();
        }
    }
}
