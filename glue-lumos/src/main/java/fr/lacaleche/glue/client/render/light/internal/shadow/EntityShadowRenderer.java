package fr.lacaleche.glue.client.render.light.internal.shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.mixin.LumosLevelRendererAccessor;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Renders nearby entities from a light's point of view into a depth map &mdash; the per-frame
 * entity shadow map, paired with {@link LightDepthSceneRenderer}'s cached terrain map.
 *
 * <p>Entities move every frame, so unlike the terrain maps this cannot be cached; it is re-rendered
 * each frame with the <em>same</em> light view/projection the terrain face used, so the deferred pass
 * samples both with one {@code lightViewProj}. Geometry is light-relative (entity world position minus
 * the light position), matching the terrain map's convention. The colour attachment is unused &mdash;
 * only the depth silhouette matters &mdash; so entities render with their normal render types and no
 * fragile format redirect is needed.</p>
 */
@Environment(EnvType.CLIENT)
public final class EntityShadowRenderer extends AbstractSceneRenderer {

    private Matrix4f lightView;
    private Matrix4f lightProj;
    private double lightX, lightY, lightZ;
    private int faceAxis = -1;
    private List<Entity> entities = List.of();
    private float partialTick;

    public EntityShadowRenderer() {
        this.clearColor = new float[]{0.0f, 0.0f, 0.0f, 1.0f};
    }

    public void configure(Matrix4f view, Matrix4f proj, double lightX, double lightY, double lightZ,
                          int faceAxis, List<Entity> entities, float partialTick) {
        this.lightView = view;
        this.lightProj = proj;
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightZ = lightZ;
        this.faceAxis = faceAxis;
        this.entities = entities;
        this.partialTick = partialTick;
    }

    /** GL depth texture id of the last render, or -1. */
    public int getDepthTextureId() {
        return framebuffer == null ? -1 : FramebufferHelper.getDepthTextureId(framebuffer);
    }

    @Override
    protected Matrix4f createProjectionMatrix(int width, int height) {
        return lightProj;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return lightView;
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
        if (client.level == null || framebuffer == null || entities.isEmpty()) return;

        EntityRenderDispatcher dispatcher = client.getEntityRenderDispatcher();
        dispatcher.setRenderShadow(false);   // no vanilla blob shadow polluting the depth map
        LumosLevelRendererAccessor levelRenderer = (LumosLevelRendererAccessor) client.levelRenderer;
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();

        // Vanilla entity draws honour the framebuffer bound at the command-encoder trySetup seam, NOT
        // a raw pre-bind or RenderSystem's output override -- so without this they leak into the main
        // framebuffer's bottom-left (our 512x512 viewport). Redirect them into this depth target the
        // same way the entity material capture does.
        GBufferCapture.beginEntityShadowCapture(FramebufferHelper.getFramebufferId(framebuffer));
        try {
            for (Entity entity : entities) {
                double ex = Mth.lerp(partialTick, entity.xOld, entity.getX());
                double ey = Mth.lerp(partialTick, entity.yOld, entity.getY());
                double ez = Mth.lerp(partialTick, entity.zOld, entity.getZ());
                if (!inFace(ex, ey, ez, entity.getBbHeight())) continue;
                // Render through vanilla's inner LevelRenderer.renderEntity, with the LIGHT as the
                // "camera", so the entity is placed light-relative (matching the light view already on
                // the RenderSystem model-view stack) and gets vanilla's interpolation, view yaw, packed
                // light and -- crucially -- the local player even in first person. (Iris's technique.)
                levelRenderer.glue$renderEntity(entity, lightX, lightY, lightZ,
                        partialTick, matrices, buffers);
            }
            buffers.endBatch();
        } finally {
            GBufferCapture.endEntityShadowCapture();
            dispatcher.setRenderShadow(true);
        }
    }

    /**
     * Whether this entity should render into the current cube face. A spot (faceAxis -1) takes every
     * entity; a point face takes an entity whose feet, middle OR head fall in that face's 90-degree
     * pyramid (three samples so a tall entity at a seam is not dropped by both faces), and always
     * takes one very near the light where the dominant-axis test is unreliable.
     */
    private boolean inFace(double ex, double ey, double ez, double height) {
        if (faceAxis < 0) return true;
        double dx = ex - lightX;
        double dy = ey - lightY;
        double dz = ez - lightZ;
        if (dx * dx + dy * dy + dz * dz < LightDepthSceneRenderer.NEAR_LIGHT * LightDepthSceneRenderer.NEAR_LIGHT) {
            return true;
        }
        return LightDepthSceneRenderer.dominantFace(dx, dy, dz) == faceAxis
                || LightDepthSceneRenderer.dominantFace(dx, dy + height * 0.5, dz) == faceAxis
                || LightDepthSceneRenderer.dominantFace(dx, dy + height, dz) == faceAxis;
    }
}
