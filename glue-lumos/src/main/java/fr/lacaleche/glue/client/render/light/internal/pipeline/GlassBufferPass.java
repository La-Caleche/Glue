package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.light.internal.scene.GlassSceneRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Re-renders nearby panes into the shared material G-buffer under the GLASS id, for the deferred
 *  and reflection passes to identify visible glass by material id rather than a depth heuristic. */
final class GlassBufferPass {

    void render(WorldLightContext context, Minecraft minecraft, LumosFrame frame,
                Vector3d camera, List<Light> all, List<Light> visible) {
        if (!GBufferCapture.isReady()) return;
        List<BlockPos> blocks = collectBlocks(context, minecraft, all, visible);
        if (blocks.isEmpty()) return;

        context.glassRenderer().configure(new Matrix4f(frame.viewMatrix()), new Matrix4f(frame.projectionMatrix()),
                camera.x, camera.y, camera.z, blocks);
        context.glassRenderer().renderRedirected(frame.width(), frame.height(), minecraft);
    }

    private static List<BlockPos> collectBlocks(WorldLightContext context, Minecraft minecraft,
                                                List<Light> all, List<Light> visible) {
        Set<Light> live = Collections.newSetFromMap(new IdentityHashMap<>());
        live.addAll(all);
        context.glassBlocks().keySet().retainAll(live);

        LinkedHashSet<BlockPos> union = new LinkedHashSet<>();
        for (Light light : visible) {
            union.addAll(context.glassBlocks().computeIfAbsent(light, candidate ->
                    GlassSceneRenderer.collectTranslucents(minecraft, candidate.x, candidate.y,
                            candidate.z, candidate.range)));
        }
        return union.isEmpty() ? List.of() : List.copyOf(union);
    }
}
