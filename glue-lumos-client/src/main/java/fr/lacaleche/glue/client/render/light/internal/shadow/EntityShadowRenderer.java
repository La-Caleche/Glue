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
        MultiBufferSource.BufferSource buffers =
                fr.lacaleche.glue.client.render.light.internal.LumosBuffers.source();

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
                if (!inFace(ex, ey, ez, entity.getBbWidth() * 0.5f, entity.getBbHeight())) continue;
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

    /** Render models (limbs mid-swing, a wither's side heads) can poke past the collision AABB. */
    private static final double FACE_PADDING = 0.5;

    /**
     * Whether this entity should render into the current cube face. A spot (faceAxis -1) takes
     * every entity; a point face takes an entity whose padded bounding box intersects that face's
     * 90-degree pyramid. The faces tile the sphere with no overlap, so an entity straddling a seam
     * must land in <em>every</em> face it touches or the shadow it casts is cut along the 45-degree
     * boundary; sampling points on the vertical centre axis missed the body's horizontal extent.
     * A false positive only draws an entity the face frustum then clips.
     */
    private boolean inFace(double ex, double ey, double ez, float halfWidth, float height) {
        if (faceAxis < 0) return true;
        double minX = ex - halfWidth - FACE_PADDING - lightX;
        double maxX = ex + halfWidth + FACE_PADDING - lightX;
        double minY = ey - FACE_PADDING - lightY;
        double maxY = ey + height + FACE_PADDING - lightY;
        double minZ = ez - halfWidth - FACE_PADDING - lightZ;
        double maxZ = ez + halfWidth + FACE_PADDING - lightZ;
        // Exact box-vs-pyramid: the pyramid is {d : towardFace >= |offAxisA|, |offAxisB|}, and the
        // best witness in an axis-aligned box takes each axis independently -- the largest reach
        // toward the face against the smallest magnitude on each off axis.
        double towardFace = switch (faceAxis) {
            case 0 -> maxX;
            case 1 -> -minX;
            case 2 -> maxY;
            case 3 -> -minY;
            case 4 -> maxZ;
            default -> -minZ;
        };
        double offA = faceAxis <= 1 ? minAbs(minY, maxY) : minAbs(minX, maxX);
        double offB = faceAxis >= 4 ? minAbs(minY, maxY) : minAbs(minZ, maxZ);
        return towardFace >= offA && towardFace >= offB;
    }

    /** Smallest |v| over {@code [min, max]}: zero when the interval straddles zero. */
    private static double minAbs(double min, double max) {
        if (min <= 0 && max >= 0) return 0;
        return Math.min(Math.abs(min), Math.abs(max));
    }
}
