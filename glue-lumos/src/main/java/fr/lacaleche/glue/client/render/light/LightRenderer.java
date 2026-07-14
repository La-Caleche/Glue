package fr.lacaleche.glue.client.render.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.render.internal.TerrainMaterialBuffer;
import fr.lacaleche.glue.client.render.light.internal.GlLightRenderer;
import fr.lacaleche.glue.client.render.light.internal.LightAccumulator;
import fr.lacaleche.glue.client.render.light.internal.ShadowParams;
import fr.lacaleche.glue.client.render.light.internal.ShadowPipelines;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.client.utils.RaycastUtils;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Facade + driver for the deferred colored-light subsystem.
 *
 * <p>Registers a {@link RenderEvents#POST_WORLD_RENDER} listener &mdash; which fires
 * after {@code LevelRenderer.renderLevel} returns (and after all Iris passes) with
 * the main render target bound. Each frame it:</p>
 * <ol>
 *   <li>bakes, or reuses, a shadow map per shadow-casting light &mdash; six per point
 *       light, one per cube face ({@link ShadowBaker});</li>
 *   <li>reconstructs world positions from the scene depth buffer and accumulates every
 *       {@link Light} into an HDR buffer, sampling its shadow map with PCSS;</li>
 *   <li>composites that buffer in linear space using captured terrain material where
 *       available and a scene-color estimate elsewhere.</li>
 * </ol>
 *
 * <p><strong>Vanilla render path.</strong> The Iris branch (reading
 * {@link RenderCompat#getIrisSceneDepthGlId()} and injecting pre-tonemap) is still a
 * marked TODO.</p>
 */
@Environment(EnvType.CLIENT)
public final class LightRenderer {

    private static final GlLightRenderer RENDERER = new GlLightRenderer();
    private static final LightAccumulator ACCUMULATOR = new LightAccumulator();

    private static boolean initialized = false;
    private static int maxSpotShadows = 6;
    private static int maxPointShadows = 4;
    private static int maxSpotBakesPerFrame = 2;
    private static int maxPointBakesPerFrame = 1;

    /** Cull distance in blocks; {@code <= 0} follows the render distance. */
    private static double maxLightDistance = 0;

    private LightRenderer() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ShadowPipelines.init();   // must be registered at init, not mid-frame
        RenderEvents.POST_WORLD_RENDER.register(LightRenderer::renderPass);
    }

    /**
     * Caps how many shadow-casting lights of each type actually get shadow maps per
     * frame (default 6 spot/gobo + 4 point). Lights past the budget still illuminate,
     * just without shadows. The recurring cost is what the budget guards: every
     * shadowed spot is a fullscreen PCSS pass per frame, and a point light is six.
     */
    public static void setShadowBudget(int spotShadows, int pointShadows) {
        maxSpotShadows = Math.max(0, spotShadows);
        maxPointShadows = Math.max(0, pointShadows);
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) context.shadows.setBudget(maxSpotShadows, maxPointShadows);
    }

    /** Limits expensive new shadow-map renders while preserving already cached maps. */
    public static void setShadowUpdateBudget(int spotBakesPerFrame, int pointBakesPerFrame) {
        maxSpotBakesPerFrame = Math.max(0, spotBakesPerFrame);
        maxPointBakesPerFrame = Math.max(0, pointBakesPerFrame);
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) {
            context.shadows.setUpdateBudget(maxSpotBakesPerFrame, maxPointBakesPerFrame);
        }
    }

    /**
     * Lights farther than this from the camera (measured to the edge of their range)
     * are skipped entirely: not accumulated, no shadow slot claimed. {@code <= 0}
     * (the default) follows the current render distance.
     */
    public static void setMaxLightDistance(double blocks) {
        maxLightDistance = blocks;
    }

    private static void renderPass() {
        // Iris shadow pass re-renders the world from the sun's POV -- skip.
        if (RenderCompat.isRenderingShadowPass()) return;

        LightManager manager = LightManager.getInstance();
        if (manager.isEmpty()) return;

        Camera camera = RaycastUtils.getLastCamera();
        if (camera == null) return;

        Minecraft mc = Minecraft.getInstance();
        WorldLightContext context = manager.currentWorld();
        if (context == null || context.level() != mc.level) return;
        RenderTarget main = mc.getMainRenderTarget();
        if (main == null) return;

        // TODO(Iris): under an active shaderpack, sample RenderCompat.getIrisSceneDepthGlId()
        // and verify the projection matches the pack's deferred math before trusting this.
        int sceneDepthId = FramebufferHelper.getDepthTextureId(main);
        if (sceneDepthId <= 0) return;

        int width = main.width;
        int height = main.height;

        // The matrices the level was ACTUALLY drawn with -- including view bobbing.
        // Rebuilding one from camera.rotation() omits the bob, and the reconstruction
        // then slides against the world every time the player walks.
        Matrix4f viewProj = FrameMatrices.getViewProjection();
        if (viewProj == null) return;
        Matrix4f invViewProj = new Matrix4f(viewProj).invert();
        Vec3 camPosVec = camera.getPosition();
        Vector3d camPos = new Vector3d(camPosVec.x, camPosVec.y, camPosVec.z);

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        List<Light> all = manager.snapshot(partialTick);

        // Point lights use their exact sphere of influence. Spot and gobo lights use
        // a tighter sphere enclosing their spherical cone sector.
        List<Light> lights = cull(mc, all, viewProj, camPos);
        if (lights.isEmpty()) return;

        // --- Pass 1: shadow maps (MC rendering). Cached -- a light that hasn't
        // moved costs nothing here after its first frame.
        context.shadows.bake(mc, RENDERER, lights);

        // --- Pass 1.5: glass G-buffer (MC rendering, camera POV). The deferred pass
        // identifies "the visible surface is glass" by matching scene depth against
        // this buffer's depth, and tints the glow with the pane's own albedo. The
        // block lists are cached per light; only the rasterisation is per-frame
        // (it depends on the camera).
        int glassColorId = -1;
        int glassDepthId = -1;
        List<BlockPos> glassBlocks = collectGlass(context, mc, all, lights);
        if (!glassBlocks.isEmpty()) {
            Matrix4f view = FrameMatrices.getView();
            Matrix4f proj = FrameMatrices.getProjection();
            if (view != null && proj != null) {
                context.glass.configure(view, proj, camPosVec.x, camPosVec.y, camPosVec.z, glassBlocks);
                context.glass.renderToTexture(width, height, mc);
                glassColorId = context.glass.getColorTextureId();
                glassDepthId = context.glass.getDepthTextureId();
            }
        }

        // --- Pass 2: deferred accumulation (raw GL) ---
        if (!ACCUMULATOR.beginFrame(width, height)) return;
        int lightFbo = ACCUMULATOR.getFramebufferId();
        if (lightFbo <= 0) return;
        boolean useTerrainMaterial = !Minecraft.useShaderTransparency();
        int materialColorId = useTerrainMaterial ? TerrainMaterialBuffer.getColorTextureId() : -1;
        int materialDepthId = useTerrainMaterial ? TerrainMaterialBuffer.getDepthTextureId() : -1;

        for (Light light : lights) {
            int[] bounds = screenBounds(light, viewProj, camPos, width, height);
            if (bounds == null) continue;
            List<ShadowParams> maps = context.shadows.get(light);
            if (maps == null || maps.isEmpty()) {
                RENDERER.accumulateLight(lightFbo, sceneDepthId, viewProj, invViewProj, camPos, light,
                        width, height, bounds, null, glassColorId, glassDepthId,
                        materialColorId, materialDepthId);
                continue;
            }
            // A point light has six maps; each pass shades only the fragments whose
            // dominant axis from the light matches that face, so they tile the sphere.
            for (ShadowParams map : maps) {
                RENDERER.accumulateLight(lightFbo, sceneDepthId, viewProj, invViewProj, camPos, light,
                        width, height, bounds, map, glassColorId, glassDepthId,
                        materialColorId, materialDepthId);
            }
        }

        // --- Pass 3: composite. Reads the scene to tint the light by the surface it
        // lands on, and cannot sample the target it writes to, so snapshot it first.
        ACCUMULATOR.captureScene(FramebufferHelper.getFramebufferId(main));
        RENDERER.compositeAdditive(ACCUMULATOR.getColorTextureId(),
                ACCUMULATOR.getSceneTextureId(), ACCUMULATOR.getSceneDepthTextureId(),
                materialColorId, materialDepthId,
                width, height);
    }

    /**
     * Drops lights that cannot contribute this frame: outside the view frustum or
     * farther than the cull distance. The viewProj is camera-relative (translation
     * lives in {@code camPos}), so the sphere test runs in that space too.
     */
    private static List<Light> cull(Minecraft mc, List<Light> all, Matrix4f viewProj, Vector3d camPos) {
        double maxDist = maxLightDistance > 0 ? maxLightDistance
                : mc.options.getEffectiveRenderDistance() * 16.0;
        FrustumIntersection frustum = new FrustumIntersection(viewProj);

        List<Light> visible = new ArrayList<>(all.size());
        for (Light light : all) {
            double centerOffset = 0.0;
            double radius = light.range;
            if (light.type != LightType.POINT) {
                double cosOuter = Math.clamp(light.cosOuter, -1.0, 1.0);
                if (cosOuter > 0.0) {
                    double sinOuter = Math.sqrt(1.0 - cosOuter * cosOuter);
                    if (cosOuter >= Math.sqrt(0.5)) {
                        centerOffset = light.range / (2.0 * cosOuter);
                        radius = centerOffset;
                    } else {
                        centerOffset = light.range * cosOuter;
                        radius = light.range * sinOuter;
                    }
                }
            }

            double dx = light.x + light.directionX * centerOffset - camPos.x;
            double dy = light.y + light.directionY * centerOffset - camPos.y;
            double dz = light.z + light.directionZ * centerOffset - camPos.z;
            if (dx * dx + dy * dy + dz * dz > (maxDist + radius) * (maxDist + radius)) continue;
            if (!frustum.testSphere((float) dx, (float) dy, (float) dz, (float) radius)) continue;
            visible.add(light);
        }
        return visible;
    }

    private static int[] screenBounds(Light light, Matrix4f viewProj, Vector3d camera,
                                      int width, int height) {
        double centerOffset = 0.0;
        double radius = light.range;
        if (light.type != LightType.POINT && light.cosOuter > 0.0f) {
            double cosine = Math.clamp(light.cosOuter, -1.0, 1.0);
            double sine = Math.sqrt(1.0 - cosine * cosine);
            if (cosine >= Math.sqrt(0.5)) {
                centerOffset = light.range / (2.0 * cosine);
                radius = centerOffset;
            } else {
                centerOffset = light.range * cosine;
                radius = light.range * sine;
            }
        }

        double cx = light.x + light.directionX * centerOffset - camera.x;
        double cy = light.y + light.directionY * centerOffset - camera.y;
        double cz = light.z + light.directionZ * centerOffset - camera.z;
        if (cx * cx + cy * cy + cz * cz <= radius * radius) {
            return new int[]{0, 0, width, height};
        }

        float minX = 1f;
        float minY = 1f;
        float maxX = -1f;
        float maxY = -1f;
        Vector4f clip = new Vector4f();
        for (int corner = 0; corner < 8; corner++) {
            clip.set((float) (cx + ((corner & 1) == 0 ? -radius : radius)),
                    (float) (cy + ((corner & 2) == 0 ? -radius : radius)),
                    (float) (cz + ((corner & 4) == 0 ? -radius : radius)), 1f);
            viewProj.transform(clip);
            if (clip.w <= 0f) return new int[]{0, 0, width, height};
            float inverseW = 1f / clip.w;
            minX = Math.min(minX, clip.x * inverseW);
            minY = Math.min(minY, clip.y * inverseW);
            maxX = Math.max(maxX, clip.x * inverseW);
            maxY = Math.max(maxY, clip.y * inverseW);
        }

        int x0 = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * width) - 2);
        int y0 = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * height) - 2);
        int x1 = Math.min(width, (int) Math.ceil((maxX * 0.5f + 0.5f) * width) + 2);
        int y1 = Math.min(height, (int) Math.ceil((maxY * 0.5f + 0.5f) * height) + 2);
        if (x1 <= x0 || y1 <= y0) return null;
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }

    /**
     * The union of every visible light's (cached) nearby translucent blocks &mdash; the
     * set the glass G-buffer rasterises this frame. Stale cache entries (lights that are
     * gone) are dropped here, keyed on identity like the shadow slots; merely culled
     * lights keep their cached lists so re-entering the frustum costs no rescan.
     */
    private static List<BlockPos> collectGlass(WorldLightContext context, Minecraft mc,
                                               List<Light> all, List<Light> visible) {
        java.util.Set<Light> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(all);
        context.glassBlocks.keySet().retainAll(live);

        LinkedHashSet<BlockPos> union = new LinkedHashSet<>();
        for (Light light : visible) {
            union.addAll(context.glassBlocks.computeIfAbsent(light, l ->
                    fr.lacaleche.glue.client.render.light.internal.GlassSceneRenderer.collectTranslucents(
                            mc, l.x, l.y, l.z, l.range)));
        }
        return union.isEmpty() ? List.of() : List.copyOf(union);
    }

    /**
     * A block changed: any light that can see it needs its shadow map re-baked.
     *
     * <p>Called from {@code LevelRendererMixin}. The maps are cached &mdash; that is what
     * makes six-face point shadows affordable &mdash; but a cache nobody invalidates just
     * tells you what the world used to look like: a broken block leaves its shadow behind,
     * and a placed one casts none.</p>
     */
    public static void onBlockChanged(BlockPos pos) {
        if (!initialized) return;
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context == null) return;
        context.shadows.invalidateAt(pos);
        // The glass block lists are cached with the same lifetime rules as the maps.
        context.glassBlocks.entrySet().removeIf(e -> {
            Light light = e.getKey();
            double dx = pos.getX() + 0.5 - light.x;
            double dy = pos.getY() + 0.5 - light.y;
            double dz = pos.getZ() + 0.5 - light.z;
            double reach = light.range + 2.0;
            return dx * dx + dy * dy + dz * dz <= reach * reach;
        });
    }

    public static void cleanup() {
        RENDERER.cleanup();
        ACCUMULATOR.cleanup();
    }

    static void configureWorld(WorldLightContext context) {
        if (context != null) {
            context.shadows.setBudget(maxSpotShadows, maxPointShadows);
            context.shadows.setUpdateBudget(maxSpotBakesPerFrame, maxPointBakesPerFrame);
        }
    }

    static void reloadResources() {
        RENDERER.cleanup();
        ACCUMULATOR.cleanup();
        WorldLightContext context = LightManager.getInstance().currentWorld();
        if (context != null) {
            context.resetRenderCaches();
            configureWorld(context);
        }
    }
}
