package fr.lacaleche.glue.client.render.light.internal.context;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightAttachment;
import fr.lacaleche.glue.client.render.light.LightHandle;
import fr.lacaleche.glue.client.render.light.internal.scene.GlassSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.scene.MetalSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.scene.WaterSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowBaker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Owns every light and world-dependent renderer cache for one client level. */
public final class WorldLightContext implements AutoCloseable {

    private final ClientLevel level;
    private final List<Light> lights = new ArrayList<>();
    private final List<LightHandle> handles = new ArrayList<>();

    final ShadowBaker shadows = new ShadowBaker();
    final GlassSceneRenderer glass = new GlassSceneRenderer();
    final WaterSceneRenderer water = new WaterSceneRenderer();
    final MetalSceneRenderer metal = new MetalSceneRenderer();
    final Map<Light, NearbyMaterials> materialBlocks = new IdentityHashMap<>();

    /** The special-material blocks near a light, split by how they are re-rendered: {@code panes} and
     *  {@code metals} via {@code renderSingleBlock}, {@code water} via {@code renderLiquid}. Cached per
     *  light and invalidated together on block changes. */
    public record NearbyMaterials(List<BlockPos> panes, List<BlockPos> water, List<BlockPos> metals) {
    }

    public WorldLightContext(ClientLevel level) {
        this.level = level;
    }

    public ClientLevel level() {
        return level;
    }

    /** Internal rendering state; not part of the light-management API. */
    public ShadowBaker shadows() {
        return shadows;
    }

    /** Internal rendering state; not part of the light-management API. */
    public GlassSceneRenderer glassRenderer() {
        return glass;
    }

    /** Internal rendering state; not part of the light-management API. */
    public WaterSceneRenderer waterRenderer() {
        return water;
    }

    /** Internal rendering state; not part of the light-management API. */
    public MetalSceneRenderer metalRenderer() {
        return metal;
    }

    /** Internal rendering state; not part of the light-management API. */
    public Map<Light, NearbyMaterials> materialBlocks() {
        return materialBlocks;
    }

    public synchronized Light add(Light light) {
        if (light != null && !containsIdentity(light)) {
            lights.add(light);
        }
        return light;
    }

    public synchronized void remove(Light light) {
        if (light == null) return;
        for (int i = 0; i < lights.size(); i++) {
            if (lights.get(i) == light) {
                lights.remove(i);
                materialBlocks.remove(light);
                shadows.remove(light);
                return;
            }
        }
    }

    public synchronized void clear() {
        lights.clear();
        handles.forEach(LightHandle::markRemoved);
        handles.clear();
        materialBlocks.clear();
        shadows.clearOwners();
    }

    public synchronized boolean isEmpty() {
        return lights.isEmpty() && handles.isEmpty();
    }

    public synchronized LightHandle attach(Light light, LightAttachment attachment) {
        if (light == null || attachment == null) {
            throw new IllegalArgumentException("Light and attachment must not be null");
        }
        LightHandle handle = new LightHandle(this, light, attachment);
        handles.add(handle);
        return handle;
    }

    public synchronized void remove(LightHandle handle) {
        handles.remove(handle);
        Light resolved = handle.resolved();
        if (resolved != null) {
            materialBlocks.remove(resolved);
            shadows.remove(resolved);
        }
        handle.markRemoved();
    }

    public synchronized void update(LightHandle handle, Light light) {
        if (handle.isRemoved() || !handles.contains(handle)) {
            throw new IllegalStateException("Cannot update a removed light handle");
        }
        Light previous = handle.resolved();
        if (previous != null) {
            materialBlocks.remove(previous);
            shadows.remove(previous);
        }
        handle.updateTemplate(light);
    }

    public synchronized void invalidateChunk(ChunkPos chunk) {
        shadows.invalidateChunk(chunk);
        int minX = chunk.getMinBlockX();
        int minZ = chunk.getMinBlockZ();
        int maxX = chunk.getMaxBlockX();
        int maxZ = chunk.getMaxBlockZ();
        materialBlocks.entrySet().removeIf(entry -> {
            Light light = entry.getKey();
            double nearestX = Math.clamp(light.x, minX, maxX);
            double nearestZ = Math.clamp(light.z, minZ, maxZ);
            double dx = light.x - nearestX;
            double dz = light.z - nearestZ;
            double reach = light.range + 2.0;
            return dx * dx + dz * dz <= reach * reach;
        });
    }

    public synchronized List<Light> snapshot(float partialTick) {
        List<Light> resolvedLights = new ArrayList<>(lights.size() + handles.size());
        resolvedLights.addAll(lights);
        Iterator<LightHandle> iterator = handles.iterator();
        while (iterator.hasNext()) {
            LightHandle handle = iterator.next();
            Light previous = handle.resolved();
            Light resolved = handle.resolve(partialTick);
            if (resolved == null) {
                if (previous != null) {
                    materialBlocks.remove(previous);
                    shadows.remove(previous);
                }
                handle.markRemoved();
                iterator.remove();
            } else {
                resolvedLights.add(resolved);
            }
        }
        return List.copyOf(resolvedLights);
    }

    private boolean containsIdentity(Light light) {
        for (Light candidate : lights) {
            if (candidate == light) return true;
        }
        return false;
    }

    /** Clears world-owned GPU caches after a resource reload. */
    public synchronized void resetRenderCaches() {
        RenderSystem.assertOnRenderThread();
        materialBlocks.clear();
        shadows.cleanup();
        glass.cleanup();
        water.cleanup();
        metal.cleanup();
    }

    @Override
    public synchronized void close() {
        RenderSystem.assertOnRenderThread();
        lights.clear();
        handles.forEach(LightHandle::markRemoved);
        handles.clear();
        materialBlocks.clear();
        shadows.cleanup();
        glass.cleanup();
        water.cleanup();
        metal.cleanup();
    }
}
