package fr.lacaleche.glue.client.render.light.internal.shadow;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightType;
import fr.lacaleche.glue.client.render.light.internal.gl.GlTintBlurPass;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 * <p>Slots are keyed by their owning {@link Light} and evicted lazily: a light that
 * drops out of the wanted set (culled, removed, or over budget) keeps its slot &mdash;
 * and its baked maps &mdash; until another light actually needs it, so a light that
 * comes back into view re-bakes nothing unless the pool ran out in the meantime.
 * {@code Light} is immutable, so a light that moves arrives as a new instance, never
 * matches its old slot, and re-bakes.</p>
 */
@Environment(EnvType.CLIENT)
public final class ShadowBaker {

    private static final int SPOT_MAP_SIZE = 1024;
    /** Point lights need six of these, so they are kept smaller. */
    private static final int POINT_FACE_SIZE = 512;

    /** Defined by @{@link fr.lacaleche.glue.client.render.light.internal.pipeline.LightRenderCoordinator} */
    private int maxSpotShadows = 0;
    private int maxPointShadows = 0;
    private int maxSpotBakesPerFrame = 0;
    private int maxPointBakesPerFrame = 0;

    private static final float NEAR = 0.05f;
    /**
     * Emitter radius in blocks. This is what PCSS turns into penumbra width, so it is the
     * knob for how hard shadows look: a true point emitter casts perfectly sharp shadows,
     * which reads as CG. A palm-sized bulb softens edges with distance from the caster,
     * the way a real lamp does.
     */
    private static final float BULB_SIZE = 0.4f;

    /** Cube face look directions, in the +X -X +Y -Y +Z -Z order the shader indexes by. */
    private static final Vector3f[] FACE_DIRS = {
            new Vector3f(1, 0, 0), new Vector3f(-1, 0, 0),
            new Vector3f(0, 1, 0), new Vector3f(0, -1, 0),
            new Vector3f(0, 0, 1), new Vector3f(0, 0, -1),
    };

    /** Entity shadow maps are re-rendered every frame; entities are small, so a modest size. */
    private static final int ENTITY_MAP_SIZE = 512;
    /** Cap on entities rendered into one light's shadow maps, to bound the per-frame cost. */
    private static final int MAX_SHADOW_ENTITIES = 16;
    /** Radians the spot cone is widened by when culling entity casters, so an entity straddling the
     *  cone rim still casts. ~14 degrees, comfortably past a typical entity's angular size past NEAR. */
    private static final double CONE_MARGIN = 0.25;

    /** One shadow-casting light's slot: its renderers, its baked maps, and who owns it. */
    private static final class Slot {
        final List<LightDepthSceneRenderer> renderers = new ArrayList<>();
        // The per-face light view/projection the terrain map was baked with, so the per-frame entity
        // pass renders with the exact same transform and both maps share one lightViewProj.
        final List<Matrix4f> faceView = new ArrayList<>();
        final List<Matrix4f> faceProj = new ArrayList<>();
        final List<EntityShadowRenderer> entityRenderers = new ArrayList<>();
        List<ShadowParams> maps = List.of();
        @Nullable
        Light owner;

        void cleanup() {
            renderers.forEach(LightDepthSceneRenderer::cleanup);
            renderers.clear();
            entityRenderers.forEach(EntityShadowRenderer::cleanup);
            entityRenderers.clear();
            faceView.clear();
            faceProj.clear();
            maps = List.of();
            owner = null;
        }
    }

    private final List<Slot> spotSlots = new ArrayList<>();
    private final List<Slot> pointSlots = new ArrayList<>();
    private final Map<Light, List<ShadowParams>> frame = new IdentityHashMap<>();
    private GlTintBlurPass tintBlur;
    private float partialTick;

    /**
     * Blur the freshly-baked transmittance map. Once, here, rather than per pixel per
     * frame in the deferred pass: the maps are cached, so this costs nothing after the
     * first frame and can use a radius wide enough to actually dissolve the block-scale
     * border pattern baked into glass textures.
     */
    private void blurTint(LightDepthSceneRenderer target, int size) {
        if (tintBlur == null) return;
        tintBlur.render(target.getFramebufferId(), target.getTintTextureId(), size);
    }

    /**
     * Caps how many lights of each type get shadow maps per frame. Shrinking the budget
     * frees the surplus slots' GPU resources immediately.
     */
    public void setBudget(int spotShadows, int pointShadows) {
        maxSpotShadows = Math.max(0, spotShadows);
        maxPointShadows = Math.max(0, pointShadows);
        trim(spotSlots, maxSpotShadows);
        trim(pointSlots, maxPointShadows);
    }

    public void setUpdateBudget(int spotBakesPerFrame, int pointBakesPerFrame) {
        maxSpotBakesPerFrame = Math.max(0, spotBakesPerFrame);
        maxPointBakesPerFrame = Math.max(0, pointBakesPerFrame);
    }

    /** Bake (or reuse) every shadow-casting light's maps. Call once per frame, before accumulation. */
    public void bake(Minecraft mc, GlTintBlurPass tintBlur, List<Light> lights, float partialTick) {
        this.tintBlur = tintBlur;
        this.partialTick = partialTick;
        frame.clear();

        List<Light> spots = new ArrayList<>();
        List<Light> points = new ArrayList<>();
        for (Light light : lights) {
            if (!light.castsShadow) continue;
            if (light.type == LightType.POINT) {
                if (points.size() < maxPointShadows) points.add(light);
            } else if (spots.size() < maxSpotShadows) {
                spots.add(light);
            }
        }

        assign(mc, spotSlots, spots, maxSpotShadows, maxSpotBakesPerFrame, 1, this::bakeSpot);
        assign(mc, pointSlots, points, maxPointShadows, maxPointBakesPerFrame, 6, this::bakePoint);
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

    public void invalidateChunk(ChunkPos chunk) {
        invalidateChunk(spotSlots, chunk);
        invalidateChunk(pointSlots, chunk);
    }

    private static void invalidateChunk(List<Slot> slots, ChunkPos chunk) {
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxX = chunk.getMaxBlockX();
        int maxZ = chunk.getMaxBlockZ();
        for (Slot slot : slots) {
            Light light = slot.owner;
            if (light == null) continue;
            double nearestX = Math.clamp(light.x, minX, maxX);
            double nearestZ = Math.clamp(light.z, minZ, maxZ);
            double dx = light.x - nearestX;
            double dz = light.z - nearestZ;
            double reach = light.range + 2.0;
            if (dx * dx + dz * dz <= reach * reach) slot.owner = null;
        }
    }

    public void remove(Light light) {
        removeOwner(spotSlots, light);
        removeOwner(pointSlots, light);
        frame.remove(light);
    }

    private static void removeOwner(List<Slot> slots, Light light) {
        for (Slot slot : slots) {
            if (slot.owner == light) {
                slot.owner = null;
                slot.maps = List.of();
            }
        }
    }

    public void clearOwners() {
        clearOwners(spotSlots);
        clearOwners(pointSlots);
        frame.clear();
    }

    private static void clearOwners(List<Slot> slots) {
        for (Slot slot : slots) {
            slot.owner = null;
            slot.maps = List.of();
        }
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

    private boolean bakeSpot(Minecraft mc, Slot slot, Light light) {
        Vector3f dir = new Vector3f(light.directionX, light.directionY, light.directionZ);
        // Frustum a little wider than the outer cone so edge shadows aren't clipped.
        float outerHalf = (float) Math.acos(Math.clamp(light.cosOuter, -1f, 1f));
        float fov = Math.min((float) Math.toRadians(170.0), outerHalf * 2f * 1.15f);

        Matrix4f proj = new Matrix4f().perspective(fov, 1f, NEAR, light.range);
        Matrix4f view = lookAlong(dir);

        // A conservative sphere around the cone. Collection is section-based and
        // clips to loaded chunks, so large ranges no longer require a dense cube scan.
        BlockPos center = BlockPos.containing(
                light.x + dir.x * (light.range * 0.5),
                light.y + dir.y * (light.range * 0.5),
                light.z + dir.z * (light.range * 0.5));
        LightDepthSceneRenderer renderer = slot.renderers.get(0);
        renderer.configure(view, proj, light.x, light.y, light.z, center, light.range,
                -1, dir, (float) Math.cos(Math.min(Math.PI, fov * 0.5 + 0.15)));
        if (renderer.renderToTexture(SPOT_MAP_SIZE, SPOT_MAP_SIZE, mc) <= 0) return false;

        boolean hasTint = renderer.hasTranslucentCasters();
        if (hasTint) blurTint(renderer, SPOT_MAP_SIZE);

        int depthId = renderer.getOpaqueDepthTextureId();
        slot.maps = depthId <= 0 ? List.of() : List.of(new ShadowParams(
                depthId,
                hasTint ? renderer.getTintTextureId() : -1,
                hasTint ? renderer.getDepthTextureId() : -1,
                new Matrix4f(proj).mul(view), SPOT_MAP_SIZE,
                NEAR, light.range, proj.m11(), BULB_SIZE, -1, -1));
        slot.faceView.clear();
        slot.faceView.add(view);
        slot.faceProj.clear();
        slot.faceProj.add(proj);
        return !slot.maps.isEmpty();
    }

    private boolean bakePoint(Minecraft mc, Slot slot, Light light) {
        // A 90 degree frustum per face is exactly the region whose dominant axis is
        // that face, so the six maps tile the sphere with no gap and no overlap.
        Matrix4f proj = new Matrix4f().perspective((float) Math.toRadians(90.0), 1f, NEAR, light.range);
        BlockPos center = BlockPos.containing(light.x, light.y, light.z);
        LightDepthSceneRenderer.Casters casters = LightDepthSceneRenderer.collectCasters(
                mc, center, light.x, light.y, light.z, light.range);

        List<ShadowParams> maps = new ArrayList<>(6);
        slot.faceView.clear();
        slot.faceProj.clear();
        for (int face = 0; face < 6; face++) {
            Matrix4f view = lookAlong(FACE_DIRS[face]);
            LightDepthSceneRenderer renderer = slot.renderers.get(face);
            renderer.configure(view, proj, light.x, light.y, light.z, center, light.range,
                    face, FACE_DIRS[face], -1f);
            renderer.useCasters(casters);
            if (renderer.renderToTexture(POINT_FACE_SIZE, POINT_FACE_SIZE, mc) <= 0) {
                slot.maps = List.of();
                return false;
            }

            boolean hasTint = renderer.hasTranslucentCasters();
            if (hasTint) blurTint(renderer, POINT_FACE_SIZE);

            int depthId = renderer.getOpaqueDepthTextureId();
            if (depthId <= 0) {
                slot.maps = List.of();
                return false;
            }
            maps.add(new ShadowParams(
                    depthId,
                    hasTint ? renderer.getTintTextureId() : -1,
                    hasTint ? renderer.getDepthTextureId() : -1,
                    new Matrix4f(proj).mul(view), POINT_FACE_SIZE,
                    NEAR, light.range, proj.m11(), BULB_SIZE, face, -1));
            slot.faceView.add(view);
            slot.faceProj.add(new Matrix4f(proj));
        }
        slot.maps = Collections.unmodifiableList(maps);
        return true;
    }

    /** View matrix looking from the origin along {@code dir} (geometry is light-relative). */
    private static Matrix4f lookAlong(Vector3f dir) {
        Vector3f up = Math.abs(dir.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        return new Matrix4f().lookAt(new Vector3f(), new Vector3f(dir), up);
    }

    // ------------------------------------------------------------------
    // Slots
    // ------------------------------------------------------------------

    private interface SlotBaker {
        boolean bake(Minecraft mc, Slot slot, Light light);
    }

    private void assign(Minecraft mc, List<Slot> slots, List<Light> wanted, int budget,
                        int updateBudget, int rendererCount, SlotBaker baker) {
        Set<Light> want = Collections.newSetFromMap(new IdentityHashMap<>());
        want.addAll(wanted);

        int bakes = 0;
        for (Light light : wanted) {
            Slot slot = slotOwnedBy(slots, light);
            if (slot == null) {
                slot = claimSlot(slots, want, budget);
                if (slot == null) continue;
            }
            while (slot.renderers.size() < rendererCount) {
                slot.renderers.add(new LightDepthSceneRenderer());
            }
            if (slot.owner != light) {
                if (bakes >= updateBudget) continue;
                slot.maps = List.of();
                if (!baker.bake(mc, slot, light)) {
                    slot.owner = null;
                    continue;
                }
                slot.owner = light;
                bakes++;
            }
            // The terrain maps above are cached; entities move, so their shadow is rendered here
            // every frame with the same per-face transform and merged into a per-frame ShadowParams.
            frame.put(light, renderEntityShadows(mc, slot, light));
        }
    }

    /**
     * Render nearby entities from the light's POV into per-face depth maps (re-rendered every frame)
     * and return {@code slot.maps} augmented with their ids. Returns the terrain maps unchanged when
     * no entity is near, so the deferred pass simply skips the entity lookup.
     */
    private List<ShadowParams> renderEntityShadows(Minecraft mc, Slot slot, Light light) {
        if (slot.maps.isEmpty() || slot.faceView.size() < slot.maps.size()) return slot.maps;
        List<Entity> entities = collectEntities(mc, light);
        if (entities.isEmpty()) return slot.maps;

        while (slot.entityRenderers.size() < slot.maps.size()) {
            slot.entityRenderers.add(new EntityShadowRenderer());
        }
        List<ShadowParams> augmented = new ArrayList<>(slot.maps.size());
        for (int i = 0; i < slot.maps.size(); i++) {
            ShadowParams params = slot.maps.get(i);
            EntityShadowRenderer renderer = slot.entityRenderers.get(i);
            renderer.configure(slot.faceView.get(i), slot.faceProj.get(i),
                    light.x, light.y, light.z, params.faceAxis(), entities, partialTick);
            int entityDepth = renderer.renderToTexture(ENTITY_MAP_SIZE, ENTITY_MAP_SIZE, mc) > 0
                    ? renderer.getDepthTextureId() : -1;
            augmented.add(params.withEntityDepth(entityDepth));
        }
        return augmented;
    }

    /**
     * The living entities whose shadow this light should render, capped at {@link #MAX_SHADOW_ENTITIES}.
     *
     * <p>When more than the cap are in range, the <em>nearest</em> are kept: they cast the most
     * prominent shadows and are the ones most likely on screen. Selecting by distance also keeps the
     * chosen set stable frame to frame -- the previous first-N-in-query-order pick flickered as entities
     * crossed the level's internal section boundaries. For a spot or gobo, entities outside the cone are
     * dropped first: the cone is convex from the light, so an occluder outside it cannot shadow any
     * receiver inside it, and rendering it would only waste a shadow slot.</p>
     */
    private static List<Entity> collectEntities(Minecraft mc, Light light) {
        if (mc.level == null) return List.of();
        double reach = light.range;
        AABB box = new AABB(light.x - reach, light.y - reach, light.z - reach,
                light.x + reach, light.y + reach, light.z + reach);

        boolean cone = light.type != LightType.POINT;
        // Cosine of the outer half-angle widened by a margin, so an entity straddling the cone rim is
        // not clipped. Compared against the direction to the entity centre.
        float coneLimit = cone
                ? (float) Math.cos(Math.min(Math.PI,
                        Math.acos(Math.clamp(light.cosOuter, -1f, 1f)) + CONE_MARGIN))
                : -1f;
        double nearSquared = LightDepthSceneRenderer.NEAR_LIGHT * LightDepthSceneRenderer.NEAR_LIGHT;

        List<Entity> entities = new ArrayList<>();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class, box,
                candidate -> !candidate.isSpectator() && !candidate.isInvisible())) {
            if (cone) {
                double dx = entity.getX() - light.x;
                double dy = entity.getY() + entity.getBbHeight() * 0.5 - light.y;
                double dz = entity.getZ() - light.z;
                double distSquared = dx * dx + dy * dy + dz * dz;
                // Very near the light the direction is unreliable and the entity dominates the map, so
                // keep it regardless -- matching the point-face selection's own near-light exception.
                if (distSquared > nearSquared) {
                    double cosine = (dx * light.directionX + dy * light.directionY + dz * light.directionZ)
                            / Math.sqrt(distSquared);
                    if (cosine < coneLimit) continue;
                }
            }
            entities.add(entity);
        }

        if (entities.size() > MAX_SHADOW_ENTITIES) {
            entities.sort(Comparator.comparingDouble(entity -> distanceSquaredToLight(light, entity)));
            return new ArrayList<>(entities.subList(0, MAX_SHADOW_ENTITIES));
        }
        return entities;
    }

    private static double distanceSquaredToLight(Light light, Entity entity) {
        double dx = entity.getX() - light.x;
        double dy = entity.getY() + entity.getBbHeight() * 0.5 - light.y;
        double dz = entity.getZ() - light.z;
        return dx * dx + dy * dy + dz * dz;
    }

    @Nullable
    private static Slot slotOwnedBy(List<Slot> slots, Light light) {
        for (Slot slot : slots) {
            if (slot.owner == light) return slot;
        }
        return null;
    }

    /** A free slot, a new one if the budget allows, else evict one whose owner is not wanted this frame. */
    @Nullable
    private static Slot claimSlot(List<Slot> slots, Set<Light> want, int budget) {
        for (Slot slot : slots) {
            if (slot.owner == null) return slot;
        }
        if (slots.size() < budget) {
            Slot slot = new Slot();
            slots.add(slot);
            return slot;
        }
        for (Slot slot : slots) {
            if (!want.contains(slot.owner)) return slot;
        }
        return null;
    }

    private static void trim(List<Slot> slots, int budget) {
        while (slots.size() > budget) {
            slots.removeLast().cleanup();
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
