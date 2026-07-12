package fr.lacaleche.glue.client.render.light.internal;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders and <strong>caches</strong> each light's shadow map(s).
 *
 * <p>Caching is the whole point. A shadow map costs a full re-rasterisation of the
 * blocks around the light, and a point light needs six of them, so baking every
 * frame does not scale past a couple of lights. Because the maps are rendered in
 * light-relative space (see {@link LightDepthSceneRenderer}) they do not depend on
 * the camera, so a light that has not moved keeps its maps indefinitely and costs
 * nothing after the first frame.</p>
 *
 * <p>Slots are assigned by position in the light list and hold onto the {@link Light}
 * they last baked. {@code Light} is immutable, so a light that moves arrives as a new
 * instance, the identity check fails, and that slot &mdash; only that slot &mdash;
 * re-bakes.</p>
 *
 * <p><strong>Known gap:</strong> a block placed or broken near a light does not
 * invalidate its map. Toggling the light re-bakes it.</p>
 */
@Environment(EnvType.CLIENT)
public final class ShadowBaker {

    private static final int SPOT_MAP_SIZE = 1024;
    /** Point lights need six of these, so they are kept smaller. */
    private static final int POINT_FACE_SIZE = 512;

    private static final int MAX_SPOT_SHADOWS = 3;
    private static final int MAX_POINT_SHADOWS = 3;

    private static final float NEAR = 0.05f;
    /**
     * Emitter radius in blocks. This is what PCSS turns into penumbra width, so it is the
     * knob for how hard shadows look: a true point emitter casts perfectly sharp shadows,
     * which reads as CG. A palm-sized bulb softens edges with distance from the caster,
     * the way a real lamp does.
     */
    private static final float BULB_SIZE = 0.25f;

    /** Cube face look directions, in the +X -X +Y -Y +Z -Z order the shader indexes by. */
    private static final Vector3f[] FACE_DIRS = {
            new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
            new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
            new Vector3f(0, 0, 1), new Vector3f(0, 0, -1),
    };

    /** One shadow-casting light's slot: its renderers, its baked maps, and who owns it. */
    private static final class Slot {
        final List<LightDepthSceneRenderer> renderers = new ArrayList<>();
        List<ShadowParams> maps = List.of();
        @Nullable
        Light owner;

        void cleanup() {
            renderers.forEach(LightDepthSceneRenderer::cleanup);
            renderers.clear();
            maps = List.of();
            owner = null;
        }
    }

    private final List<Slot> spotSlots = new ArrayList<>();
    private final List<Slot> pointSlots = new ArrayList<>();
    private final Map<Light, List<ShadowParams>> frame = new IdentityHashMap<>();
    private GlLightRenderer glRenderer;

    /**
     * Blur the freshly-baked transmittance map. Once, here, rather than per pixel per
     * frame in the deferred pass: the maps are cached, so this costs nothing after the
     * first frame and can use a radius wide enough to actually dissolve the block-scale
     * border pattern baked into glass textures.
     */
    private void blurTint(LightDepthSceneRenderer target, int size) {
        if (glRenderer == null) return;
        glRenderer.blurTint(target.getFramebufferId(), target.getTintTextureId(), size);
    }

    /** Bake (or reuse) every shadow-casting light's maps. Call once per frame, before accumulation. */
    public void bake(Minecraft mc, GlLightRenderer glRenderer, List<Light> lights) {
        this.glRenderer = glRenderer;
        frame.clear();
        int spotIndex = 0;
        int pointIndex = 0;

        for (Light light : lights) {
            if (!light.castsShadow) continue;

            if (light.type == LightType.POINT) {
                if (pointIndex >= MAX_POINT_SHADOWS) continue;
                Slot slot = acquire(pointSlots, pointIndex++, 6);
                if (slot.owner != light) {
                    bakePoint(mc, slot, light);
                    slot.owner = light;
                }
                frame.put(light, slot.maps);
            } else {
                if (spotIndex >= MAX_SPOT_SHADOWS) continue;
                Slot slot = acquire(spotSlots, spotIndex++, 1);
                if (slot.owner != light) {
                    bakeSpot(mc, slot, light);
                    slot.owner = light;
                }
                frame.put(light, slot.maps);
            }
        }

        // Drop maps for slots nobody claimed, so a stale light can't keep being sampled.
        releaseUnused(spotSlots, spotIndex);
        releaseUnused(pointSlots, pointIndex);
    }

    /** The maps baked for {@code light} this frame, or {@code null} if it casts no shadow. */
    @Nullable
    public List<ShadowParams> get(Light light) {
        return frame.get(light);
    }

    /**
     * Drop the cached maps of every light that can see {@code pos}, so they re-bake next
     * frame.
     *
     * <p>Without this the cache is a liar: the map still holds the geometry that was there
     * when the light was last baked, so breaking a block leaves its shadow behind and
     * placing one casts none. Forgetting the owner is all it takes &mdash; the slot no
     * longer matches its light, so {@link #bake} re-renders it.</p>
     */
    public void invalidateAt(BlockPos pos) {
        invalidateIn(spotSlots, pos);
        invalidateIn(pointSlots, pos);
    }

    private static void invalidateIn(List<Slot> slots, BlockPos pos) {
        for (Slot slot : slots) {
            Light light = slot.owner;
            if (light == null) continue;

            double dx = pos.getX() + 0.5 - light.x;
            double dy = pos.getY() + 0.5 - light.y;
            double dz = pos.getZ() + 0.5 - light.z;
            // +2 of slack: a block just outside the range can still be a caster, because
            // the bake region is sized to the range rather than clipped exactly to it.
            double reach = light.range + 2.0;
            if (dx * dx + dy * dy + dz * dz <= reach * reach) {
                slot.owner = null;
            }
        }
    }

    // ------------------------------------------------------------------
    // Baking
    // ------------------------------------------------------------------

    private void bakeSpot(Minecraft mc, Slot slot, Light light) {
        Vector3f dir = new Vector3f(light.direction);
        // Frustum a little wider than the outer cone so edge shadows aren't clipped.
        float outerHalf = (float) Math.acos(Math.max(-1f, Math.min(1f, light.cosOuter)));
        float fov = Math.min((float) Math.toRadians(170.0), outerHalf * 2f * 1.15f);

        Matrix4f proj = new Matrix4f().perspective(fov, 1f, NEAR, light.range);
        Matrix4f view = lookAlong(dir);

        // Blocks around the middle of the cone; the radius roughly covers the footprint.
        BlockPos center = BlockPos.containing(
                light.x + dir.x * (light.range * 0.5),
                light.y + dir.y * (light.range * 0.5),
                light.z + dir.z * (light.range * 0.5));
        int radius = Math.max(8, Math.min(18, (int) Math.ceil(light.range * 0.6)));

        LightDepthSceneRenderer renderer = slot.renderers.get(0);
        renderer.configure(view, proj, light.x, light.y, light.z, center, radius, light.range,
                -1, dir, (float) Math.cos(Math.min(Math.PI, fov * 0.5 + 0.15)));
        renderer.renderToTexture(SPOT_MAP_SIZE, SPOT_MAP_SIZE, mc);

        boolean hasTint = renderer.hasTranslucentCasters();
        if (hasTint) blurTint(renderer, SPOT_MAP_SIZE);

        int depthId = renderer.getOpaqueDepthTextureId();
        slot.maps = depthId <= 0 ? List.of() : List.of(new ShadowParams(
                depthId,
                hasTint ? renderer.getTintTextureId() : -1,
                hasTint ? renderer.getDepthTextureId() : -1,
                new Matrix4f(proj).mul(view), SPOT_MAP_SIZE,
                NEAR, light.range, proj.m11(), BULB_SIZE, -1));
    }

    private void bakePoint(Minecraft mc, Slot slot, Light light) {
        // A 90 degree frustum per face is exactly the region whose dominant axis is
        // that face, so the six maps tile the sphere with no gap and no overlap.
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(90.0), 1f, NEAR, light.range);
        BlockPos center = BlockPos.containing(light.x, light.y, light.z);
        int radius = Math.max(4, Math.min(16, (int) Math.ceil(light.range)));

        List<ShadowParams> maps = new ArrayList<>(6);
        for (int face = 0; face < 6; face++) {
            Matrix4f view = lookAlong(FACE_DIRS[face]);
            LightDepthSceneRenderer renderer = slot.renderers.get(face);
            renderer.configure(view, proj, light.x, light.y, light.z, center, radius, light.range,
                    face, FACE_DIRS[face], -1f);
            renderer.renderToTexture(POINT_FACE_SIZE, POINT_FACE_SIZE, mc);

            boolean hasTint = renderer.hasTranslucentCasters();
            if (hasTint) blurTint(renderer, POINT_FACE_SIZE);

            int depthId = renderer.getOpaqueDepthTextureId();
            if (depthId <= 0) {
                slot.maps = List.of();
                return;
            }
            maps.add(new ShadowParams(
                    depthId,
                    hasTint ? renderer.getTintTextureId() : -1,
                    hasTint ? renderer.getDepthTextureId() : -1,
                    new Matrix4f(proj).mul(view), POINT_FACE_SIZE,
                    NEAR, light.range, proj.m11(), BULB_SIZE, face));
        }
        slot.maps = Collections.unmodifiableList(maps);
    }

    /** View matrix looking from the origin along {@code dir} (geometry is light-relative). */
    private static Matrix4f lookAlong(Vector3f dir) {
        Vector3f up = Math.abs(dir.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        return new Matrix4f().lookAt(new Vector3f(), new Vector3f(dir), up);
    }

    // ------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------

    private static Slot acquire(List<Slot> slots, int index, int rendererCount) {
        while (slots.size() <= index) {
            slots.add(new Slot());
        }
        Slot slot = slots.get(index);
        while (slot.renderers.size() < rendererCount) {
            slot.renderers.add(new LightDepthSceneRenderer());
        }
        return slot;
    }

    private static void releaseUnused(List<Slot> slots, int usedCount) {
        for (int i = usedCount; i < slots.size(); i++) {
            slots.get(i).owner = null;
            slots.get(i).maps = List.of();
        }
    }

    public void cleanup() {
        spotSlots.forEach(Slot::cleanup);
        pointSlots.forEach(Slot::cleanup);
        spotSlots.clear();
        pointSlots.clear();
        frame.clear();
    }
}
