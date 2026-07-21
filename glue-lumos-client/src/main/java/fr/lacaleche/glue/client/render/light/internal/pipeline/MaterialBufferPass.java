package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.light.internal.scene.MaterialBlockScan;
import fr.lacaleche.glue.client.render.light.internal.scene.MaterialBlockScan.NearbyMaterials;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Re-renders nearby special-material surfaces into the shared material G-buffer under their material
 *  ids (glass = 4, water = 5, metal = 6), so the deferred and reflection passes can give each its own
 *  response rather than the diffuse terrain default. Panes and metals are drawn as block models, water
 *  as fluid geometry. */
final class MaterialBufferPass {

    void render(WorldLightContext context, Minecraft minecraft, LumosFrame frame,
                Vector3d camera, List<Light> all, List<Light> visible) {
        if (!GBufferCapture.isReady()) return;

        Set<Light> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(all);
        context.materialBlocks().keySet().retainAll(live);

        // On a reduced-capture frame (Iris shaderpack) the scene's own draws could not capture base
        // terrain, so the pass re-renders it here -- FIRST, so the special materials below overwrite
        // its id at shared pixels, matching the native path's ordering.
        boolean reduced = GBufferCapture.isReducedCapture();
        LinkedHashSet<BlockPos> panes = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> metals = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> terrain = new LinkedHashSet<>();
        for (Light light : visible) {
            NearbyMaterials nearby = context.materialBlocks().computeIfAbsent(light, candidate ->
                    MaterialBlockScan.scan(minecraft, candidate.x, candidate.y,
                            candidate.z, candidate.range));
            panes.addAll(nearby.panes());
            water.addAll(nearby.water());
            metals.addAll(nearby.metals());
            if (reduced) terrain.addAll(nearby.terrain());
        }

        Matrix4f view = new Matrix4f(frame.viewMatrix());
        Matrix4f projection = new Matrix4f(frame.projectionMatrix());
        if (!terrain.isEmpty()) {
            context.terrainRenderer().configure(new Matrix4f(view), new Matrix4f(projection),
                    camera.x, camera.y, camera.z, List.copyOf(terrain));
            context.terrainRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
        if (reduced) {
            List<Entity> entities = collectEntities(minecraft, visible);
            if (!entities.isEmpty()) {
                float partialTick = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
                context.entityMaterialRenderer().configure(new Matrix4f(view),
                        new Matrix4f(projection), camera.x, camera.y, camera.z,
                        entities, partialTick);
                context.entityMaterialRenderer()
                        .renderRedirected(frame.width(), frame.height(), minecraft);
            }
        }
        if (!panes.isEmpty()) {
            context.glassRenderer().configure(new Matrix4f(view), new Matrix4f(projection),
                    camera.x, camera.y, camera.z, List.copyOf(panes));
            context.glassRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
        if (!water.isEmpty()) {
            context.waterRenderer().configure(new Matrix4f(view), new Matrix4f(projection),
                    camera.x, camera.y, camera.z, List.copyOf(water));
            context.waterRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
        if (!metals.isEmpty()) {
            context.metalRenderer().configure(new Matrix4f(view), new Matrix4f(projection),
                    camera.x, camera.y, camera.z, List.copyOf(metals));
            context.metalRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
    }

    /** Every living entity within any visible light's reach, for the reduced-frame entity
     *  re-capture. The first-person camera entity is skipped: the pack drew no body for it, so its
     *  fragments would only stamp stale ids over pixels other surfaces captured correctly. */
    private static List<Entity> collectEntities(Minecraft minecraft, List<Light> visible) {
        if (minecraft.level == null) return List.of();
        boolean firstPerson = minecraft.options.getCameraType().isFirstPerson();
        Entity cameraEntity = minecraft.getCameraEntity();
        Set<Entity> entities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Light light : visible) {
            double reach = light.range;
            AABB box = new AABB(light.x - reach, light.y - reach, light.z - reach,
                    light.x + reach, light.y + reach, light.z + reach);
            entities.addAll(minecraft.level.getEntitiesOfClass(LivingEntity.class, box,
                    candidate -> !candidate.isSpectator() && !candidate.isInvisible()
                            && !(firstPerson && candidate == cameraEntity)));
        }
        return List.copyOf(entities);
    }
}
