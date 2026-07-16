package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext.NearbyTranslucents;
import fr.lacaleche.glue.client.render.light.internal.scene.GlassSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.scene.WaterSceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Re-renders nearby translucent surfaces into the shared material G-buffer under their material ids
 *  (glass = 4, water = 5), so the deferred and reflection passes can identify each by material id
 *  rather than a depth heuristic. Panes are drawn as block models, water as fluid geometry. */
final class TranslucentBufferPass {

    void render(WorldLightContext context, Minecraft minecraft, LumosFrame frame,
                Vector3d camera, List<Light> all, List<Light> visible) {
        if (!GBufferCapture.isReady()) return;

        Set<Light> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(all);
        context.translucentBlocks().keySet().retainAll(live);

        LinkedHashSet<BlockPos> panes = new LinkedHashSet<>();
        LinkedHashSet<BlockPos> water = new LinkedHashSet<>();
        for (Light light : visible) {
            NearbyTranslucents nearby = context.translucentBlocks().computeIfAbsent(light, candidate ->
                    new NearbyTranslucents(
                            GlassSceneRenderer.collectTranslucents(minecraft, candidate.x, candidate.y,
                                    candidate.z, candidate.range),
                            WaterSceneRenderer.collectWater(minecraft, candidate.x, candidate.y,
                                    candidate.z, candidate.range)));
            panes.addAll(nearby.panes());
            water.addAll(nearby.water());
        }

        Matrix4f view = new Matrix4f(frame.viewMatrix());
        Matrix4f projection = new Matrix4f(frame.projectionMatrix());
        if (!panes.isEmpty()) {
            context.glassRenderer().configure(view, projection, camera.x, camera.y, camera.z,
                    List.copyOf(panes));
            context.glassRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
        if (!water.isEmpty()) {
            context.waterRenderer().configure(new Matrix4f(view), new Matrix4f(projection),
                    camera.x, camera.y, camera.z, List.copyOf(water));
            context.waterRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
        }
    }
}
