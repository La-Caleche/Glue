package fr.lacaleche.glue.client.render.light.internal.pipeline;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.client.render.light.internal.LightManager;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/** Owns Lumos frame admission, visibility, pass ordering, and global render resources. */
public final class LightRenderCoordinator {

    // TEMPORARY diagnostic: -Dglue.lumos.irisStage=off|blit|shadow|material|full limits how far
    // the Iris-frame path runs, to bisect state leaks. Remove once the Iris mode is stable.
    private static final String IRIS_STAGE = System.getProperty("glue.lumos.irisStage", "full");

    private final MaterialBufferPass materialPass = new MaterialBufferPass();
    private final DeferredLightPass deferredPass = new DeferredLightPass();

    private int maxSpotShadows = 15;
    private int maxPointShadows = 15;
    private int maxSpotBakesPerFrame = 5;
    private int maxPointBakesPerFrame = 5;
    private double maxLightDistance;

    public void render() {
        LightManager manager = LightManager.getInstance();
        if (manager.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        WorldLightContext context = manager.currentWorld();
        if (context == null || context.level() != minecraft.level) return;

        // Fabulous composites translucents from a separate target, so the main depth would not
        // match the scene; Lumos does not run under it.
        if (Minecraft.useShaderTransparency()) return;

        RenderTarget main = minecraft.getMainRenderTarget();
        Camera mainCamera = minecraft.gameRenderer.getMainCamera();
        Matrix4f view = FrameMatrices.getView();
        Matrix4f projection = FrameMatrices.getProjection();
        if (view == null || projection == null) return;
        int fbo = FramebufferHelper.getFramebufferId(main);
        int depth = FramebufferHelper.getDepthTextureId(main);

        // On an Iris shaderpack frame the scene lives elsewhere: depth in Iris's own targets, and
        // colour in whatever framebuffer the pack's compositing left bound at this seam (the same
        // one the post-effect chain reads and writes -- the proven mechanics). Lumos keeps working
        // against the main target: the scene is blitted in, lit, and blitted back. If the Iris
        // internals ever drift and the depth cannot be found, lights stay off rather than corrupt
        // the pack's frame.
        boolean irisPack = RenderCompat.isIrisShaderEnabled();
        int packSceneFbo = 0;
        if (irisPack) {
            depth = RenderCompat.getIrisMainDepthGlId();
            packSceneFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            if (depth <= 0 || packSceneFbo <= 0) return;
        }
        if (fbo <= 0 || depth <= 0) return;
        LumosFrame frame = new LumosFrame(fbo, depth, main.width, main.height, view, projection);

        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
        Vector3d camera = new Vector3d(mainCamera.getPosition().x, mainCamera.getPosition().y,
                mainCamera.getPosition().z);
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Light> all = manager.snapshot(partialTick);
        List<Light> visible = cull(minecraft, all, viewProjection, camera);
        if (visible.isEmpty()) return;

        if (irisPack) {
            if ("off".equals(IRIS_STAGE)) return;
            // The bypass keeps Iris away from the vanilla-path draws inside (shadow bakes, block
            // and entity re-renders): no vertex-format extension, no program overrides.
            int packFbo = packSceneFbo;
            SavedGlState state = SavedGlState.save();
            try {
                blitColor(packFbo, fbo, main.width, main.height);
                if (!"blit".equals(IRIS_STAGE)) {
                    RenderCompat.withIrisBypass(() -> renderPasses(context, minecraft, frame,
                            viewProjection, inverseViewProjection, camera, all, visible,
                            partialTick, IRIS_STAGE));
                }
                blitColor(fbo, packFbo, main.width, main.height);
            } finally {
                state.restore();
            }
        } else {
            renderPasses(context, minecraft, frame, viewProjection, inverseViewProjection,
                    camera, all, visible, partialTick, "full");
        }
    }

    private void renderPasses(WorldLightContext context, Minecraft minecraft, LumosFrame frame,
                              Matrix4f viewProjection, Matrix4f inverseViewProjection,
                              Vector3d camera, List<Light> all, List<Light> visible,
                              float partialTick, String stage) {
        context.shadows().bake(minecraft, deferredPass.tintBlur(), visible, partialTick,
                context::shadowAnchor);
        if ("shadow".equals(stage)) return;
        materialPass.render(context, minecraft, frame, camera, all, visible);
        if ("material".equals(stage)) return;
        deferredPass.render(frame, viewProjection, inverseViewProjection, camera,
                visible, context.shadows(), minecraft, partialTick);
    }

    /** Plain colour blit between the pack's scene framebuffer and the main target — deliberately
     *  the same minimal discipline as the post-effect chain's blits, which are proven to move the
     *  visible scene at this seam without disturbing Iris's framebuffer state. */
    private static void blitColor(int srcFbo, int dstFbo, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    public void setShadowBudget(int spots, int points) {
        maxSpotShadows = Math.max(0, spots);
        maxPointShadows = Math.max(0, points);
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) context.shadows().setBudget(maxSpotShadows, maxPointShadows);
    }

    public void setShadowUpdateBudget(int spots, int points) {
        maxSpotBakesPerFrame = Math.max(0, spots);
        maxPointBakesPerFrame = Math.max(0, points);
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) {
            context.shadows().setUpdateBudget(maxSpotBakesPerFrame, maxPointBakesPerFrame);
        }
    }

    public void setMaxLightDistance(double blocks) {
        maxLightDistance = blocks;
    }

    public void configureWorld(WorldLightContext context) {
        if (context == null) return;
        context.shadows().setBudget(maxSpotShadows, maxPointShadows);
        context.shadows().setUpdateBudget(maxSpotBakesPerFrame, maxPointBakesPerFrame);
    }

    public void onBlockChanged(BlockPos position) {
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context == null) return;
        context.shadows().invalidateAt(position);
        context.materialBlocks().entrySet().removeIf(entry -> reaches(entry.getKey(), position));
    }

    public void reloadResources() {
        deferredPass.cleanup();
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) {
            context.resetRenderCaches();
            configureWorld(context);
        }
    }

    public void cleanup() {
        deferredPass.cleanup();
        fr.lacaleche.glue.client.render.light.internal.LumosBuffers.cleanup();
        // The shadow maps and re-render targets live on the world context, so releasing only the
        // deferred pass would leave the largest allocations resident.
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) context.resetRenderCaches();
    }

    private List<Light> cull(Minecraft minecraft, List<Light> all,
                             Matrix4f viewProjection, Vector3d camera) {
        double maximumDistance = maxLightDistance > 0 ? maxLightDistance
                : minecraft.options.getEffectiveRenderDistance() * 16.0;
        FrustumIntersection frustum = new FrustumIntersection(viewProjection);
        List<Light> visible = new ArrayList<>(all.size());

        for (Light light : all) {
            LightInfluence sphere = LightInfluence.of(light, camera);
            double maximumReach = maximumDistance + sphere.radius();
            if (sphere.x() * sphere.x() + sphere.y() * sphere.y() + sphere.z() * sphere.z()
                    > maximumReach * maximumReach) continue;
            if (!frustum.testSphere((float) sphere.x(), (float) sphere.y(),
                    (float) sphere.z(), (float) sphere.radius())) continue;
            visible.add(light);
        }
        return visible;
    }

    private static boolean reaches(Light light, BlockPos position) {
        double x = position.getX() + 0.5 - light.x;
        double y = position.getY() + 0.5 - light.y;
        double z = position.getZ() + 0.5 - light.z;
        double reach = light.range + 2.0;
        return x * x + y * y + z * z <= reach * reach;
    }

}
