package fr.lacaleche.glue.client.render.light;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.render.light.internal.GlLightRenderer;
import fr.lacaleche.glue.client.render.light.internal.GlassSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.LightAccumulator;
import fr.lacaleche.glue.client.render.light.internal.ShadowBaker;
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
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
 *   <li>tonemaps that buffer and composites it onto the scene, tinted by the surface
 *       it lands on.</li>
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
    private static final ShadowBaker SHADOWS = new ShadowBaker();
    private static final GlassSceneRenderer GLASS = new GlassSceneRenderer();

    /**
     * Translucent blocks within each light's reach, keyed on {@link Light} identity like
     * the shadow slots (a moved light is a new instance). The scan is the expensive part;
     * the per-frame G-buffer render just rasterises these few blocks. Invalidated by
     * {@link #onBlockChanged} the same way the shadow maps are.
     */
    private static final Map<Light, List<BlockPos>> GLASS_BLOCKS = new IdentityHashMap<>();

    private static boolean initialized = false;

    private LightRenderer() {
    }

    public static void init() {
        if (initialized) return;
        initialized = true;
        ShadowPipelines.init();   // must be registered at init, not mid-frame
        RenderEvents.POST_WORLD_RENDER.register(LightRenderer::renderPass);
    }

    private static void renderPass() {
        // Iris shadow pass re-renders the world from the sun's POV -- skip.
        if (RenderCompat.isRenderingShadowPass()) return;

        LightManager manager = LightManager.getInstance();
        if (manager.isEmpty()) return;

        Camera camera = RaycastUtils.getLastCamera();
        if (camera == null) return;

        Minecraft mc = Minecraft.getInstance();
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

        List<Light> lights = manager.snapshot();

        // --- Pass 1: shadow maps (MC rendering). Cached -- a light that hasn't
        // moved costs nothing here after its first frame.
        SHADOWS.bake(mc, RENDERER, lights);

        // --- Pass 1.5: glass G-buffer (MC rendering, camera POV). The deferred pass
        // identifies "the visible surface is glass" by matching scene depth against
        // this buffer's depth, and tints the glow with the pane's own albedo. The
        // block lists are cached per light; only the rasterisation is per-frame
        // (it depends on the camera).
        int glassColorId = -1;
        int glassDepthId = -1;
        List<BlockPos> glassBlocks = collectGlass(mc, lights);
        if (!glassBlocks.isEmpty()) {
            Matrix4f view = FrameMatrices.getView();
            Matrix4f proj = FrameMatrices.getProjection();
            if (view != null && proj != null) {
                GLASS.configure(view, proj, camPosVec.x, camPosVec.y, camPosVec.z, glassBlocks);
                GLASS.renderToTexture(width, height, mc);
                glassColorId = GLASS.getColorTextureId();
                glassDepthId = GLASS.getDepthTextureId();
            }
        }

        // --- Pass 2: deferred accumulation (raw GL) ---
        ACCUMULATOR.beginFrame(width, height);
        int lightFbo = ACCUMULATOR.getFramebufferId();
        if (lightFbo <= 0) return;

        for (Light light : lights) {
            List<ShadowParams> maps = SHADOWS.get(light);
            if (maps == null || maps.isEmpty()) {
                RENDERER.accumulateLight(lightFbo, sceneDepthId, viewProj, invViewProj, camPos, light,
                        width, height, null, glassColorId, glassDepthId);
                continue;
            }
            // A point light has six maps; each pass shades only the fragments whose
            // dominant axis from the light matches that face, so they tile the sphere.
            for (ShadowParams map : maps) {
                RENDERER.accumulateLight(lightFbo, sceneDepthId, viewProj, invViewProj, camPos, light,
                        width, height, map, glassColorId, glassDepthId);
            }
        }

        // --- Pass 3: composite. Reads the scene to tint the light by the surface it
        // lands on, and cannot sample the target it writes to, so snapshot it first.
        ACCUMULATOR.captureScene(FramebufferHelper.getFramebufferId(main));
        RENDERER.compositeAdditive(ACCUMULATOR.getColorTextureId(),
                ACCUMULATOR.getSceneTextureId(), width, height);
    }

    /**
     * The union of every light's (cached) nearby translucent blocks &mdash; the set the
     * glass G-buffer rasterises this frame. Stale cache entries (lights that are gone)
     * are dropped here, keyed on identity like the shadow slots.
     */
    private static List<BlockPos> collectGlass(Minecraft mc, List<Light> lights) {
        java.util.Set<Light> live = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(lights);
        GLASS_BLOCKS.keySet().retainAll(live);

        LinkedHashSet<BlockPos> union = new LinkedHashSet<>();
        for (Light light : lights) {
            union.addAll(GLASS_BLOCKS.computeIfAbsent(light, l ->
                    GlassSceneRenderer.collectTranslucents(mc, l.x, l.y, l.z, l.range)));
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
        SHADOWS.invalidateAt(pos);
        // The glass block lists are cached with the same lifetime rules as the maps.
        GLASS_BLOCKS.entrySet().removeIf(e -> {
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
        SHADOWS.cleanup();
        GLASS.cleanup();
        GLASS_BLOCKS.clear();
    }
}
