package fr.lacaleche.glue.client.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Deferred draw queue for raw GL rendering.
 * <p>
 * Collects draw commands during block entity rendering (which happens in the main pass)
 * and executes them after all world rendering passes (terrain, entities, particles, clouds,
 * weather) have completed. This ensures custom shader quads are not occluded by clouds
 * or other late-pass effects.
 * <p>
 * Must be initialized once during client startup via {@link #init()}.
 */
@Environment(EnvType.CLIENT)
public class DeferredDrawQueue {

    private static final List<DrawCommand> pendingDraws = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * Registers the WorldRenderEvents.LAST listener to flush the queue.
     * Call once during client mod initialization.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        WorldRenderEvents.LAST.register(context -> flush());
    }

    /**
     * Queues a draw command to be executed after all world render passes.
     */
    static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        pendingDraws.add(new DrawCommand(new Matrix4f(mvp), vertices.clone(), colors.clone(), vertexCount));
    }

    /**
     * Executes all pending draw commands and clears the queue.
     */
    private static void flush() {
        if (pendingDraws.isEmpty()) return;

        for (DrawCommand cmd : pendingDraws) {
            GlDirectRenderer.drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount);
        }
        pendingDraws.clear();
    }

    private record DrawCommand(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {}
}
