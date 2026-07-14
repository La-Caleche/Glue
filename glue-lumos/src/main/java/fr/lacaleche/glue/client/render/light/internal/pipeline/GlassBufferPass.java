package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.client.render.light.internal.scene.GlassSceneRenderer;
import fr.lacaleche.glue.client.render.pipeline.WorldRenderFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.joml.Matrix4f;
import org.joml.Vector3d;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds the camera-space glass textures consumed by deferred lighting. */
final class GlassBufferPass {

    Textures render(WorldLightContext context, Minecraft minecraft, WorldRenderFrame frame,
                    Vector3d camera, List<Light> all, List<Light> visible) {
        List<BlockPos> blocks = collectBlocks(context, minecraft, all, visible);
        if (blocks.isEmpty()) return Textures.NONE;

        context.glassRenderer().configure(new Matrix4f(frame.viewMatrix()), new Matrix4f(frame.projectionMatrix()),
                camera.x, camera.y, camera.z, blocks);
        if (context.glassRenderer().renderToTexture(frame.width(), frame.height(), minecraft) <= 0) {
            return Textures.NONE;
        }
        return new Textures(context.glassRenderer().getColorTextureId(),
                context.glassRenderer().getDepthTextureId());
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

    record Textures(int colorId, int depthId) {
        static final Textures NONE = new Textures(-1, -1);
    }
}
