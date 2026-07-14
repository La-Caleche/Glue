package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightManager;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderFrame;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/** Owns Lumos frame admission, visibility, pass ordering, and global render resources. */
public final class LightRenderCoordinator {

    private final GlassBufferPass glassPass = new GlassBufferPass();
    private final DeferredLightPass deferredPass = new DeferredLightPass();

    private int maxSpotShadows = 6;
    private int maxPointShadows = 4;
    private int maxSpotBakesPerFrame = 2;
    private int maxPointBakesPerFrame = 1;
    private double maxLightDistance;

    public void render() {
        if (WorldRenderPipelines.isAuxiliaryPass()) return;

        LightManager manager = LightManager.getInstance();
        if (manager.isEmpty()) return;

        Minecraft minecraft = Minecraft.getInstance();
        WorldLightContext context = manager.currentWorld();
        if (context == null || context.level() != minecraft.level) return;

        WorldRenderFrame frame = WorldRenderPipelines.currentFrame().orElse(null);
        if (frame == null) return;
        if (frame.colorEncoding() != WorldRenderFrame.ColorEncoding.SRGB
                || frame.compositeStage() != WorldRenderFrame.CompositeStage.FINAL_COLOR) return;

        Matrix4f viewProjection = frame.viewProjection();
        Matrix4f inverseViewProjection = new Matrix4f(viewProjection).invert();
        Vector3d camera = new Vector3d(frame.cameraPosition());
        float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Light> all = manager.snapshot(partialTick);
        List<Light> visible = cull(minecraft, all, viewProjection, camera);
        if (visible.isEmpty()) return;

        context.shadows().bake(minecraft, deferredPass.tintBlur(), visible);
        GlassBufferPass.Textures glass = glassPass.render(context, minecraft, frame, camera, all, visible);
        deferredPass.render(frame, viewProjection, inverseViewProjection, camera,
                visible, context.shadows(), glass);
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
        context.glassBlocks().entrySet().removeIf(entry -> reaches(entry.getKey(), position));
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
