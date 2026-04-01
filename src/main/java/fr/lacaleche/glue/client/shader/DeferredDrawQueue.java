package fr.lacaleche.glue.client.shader;

import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Iris-aware draw queue for raw GL rendering.
 * Defers draws to WorldRenderEvents.LAST when Iris is active, executes immediately otherwise.
 */
@Environment(EnvType.CLIENT)
public class DeferredDrawQueue {

    private static final List<DrawCommand> pendingDraws = new ArrayList<>();
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.LAST.register(context -> flush());
    }

    static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            pendingDraws.add(new DrawCommand(new Matrix4f(mvp), vertices.clone(), colors.clone(), vertexCount));
        } else {
            GlDirectRenderer.drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    private static void flush() {
        if (pendingDraws.isEmpty()) return;

        for (DrawCommand cmd : pendingDraws) {
            GlDirectRenderer.drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount, true);
        }
        pendingDraws.clear();
    }

    private record DrawCommand(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {}
}
