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

    // ── Colored quad ─────────────────────────────────────────────

    static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            pendingDraws.add(new QuadCommand(new Matrix4f(mvp), vertices.clone(), colors.clone(), vertexCount));
        } else {
            GlDirectRenderer.drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    // ── Textured quad ────────────────────────────────────────────

    static void enqueueTextured(Matrix4f mvp, float[] vertices, float[] uvs,
                                float[] colors, int textureId, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            pendingDraws.add(new TexturedQuadCommand(
                    new Matrix4f(mvp), vertices.clone(), uvs.clone(), colors.clone(), textureId, vertexCount));
        } else {
            GlDirectRenderer.drawTexturedQuad(mvp, vertices, uvs, colors, textureId, vertexCount, true);
        }
    }

    // ── Arbitrary deferred action ────────────────────────────────

    /**
     * Defers an arbitrary rendering action to after Iris shadow passes.
     * If Iris is not active, the action executes immediately.
     */
    public static void defer(Runnable action) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            pendingDraws.add(new RunnableCommand(action));
        } else {
            action.run();
        }
    }

    // ── Flush ────────────────────────────────────────────────────

    private static void flush() {
        if (pendingDraws.isEmpty()) return;

        for (DrawCommand cmd : pendingDraws) {
            switch (cmd) {
                case QuadCommand q ->
                        GlDirectRenderer.drawQuad(q.mvp, q.vertices, q.colors, q.vertexCount, true);
                case TexturedQuadCommand t ->
                        GlDirectRenderer.drawTexturedQuad(t.mvp, t.vertices, t.uvs, t.colors, t.textureId, t.vertexCount, true);
                case RunnableCommand r ->
                        r.action.run();
            }
        }
        pendingDraws.clear();
    }

    // ── Command types ────────────────────────────────────────────

    private sealed interface DrawCommand permits QuadCommand, TexturedQuadCommand, RunnableCommand {}
    private record QuadCommand(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) implements DrawCommand {}
    private record TexturedQuadCommand(Matrix4f mvp, float[] vertices, float[] uvs, float[] colors, int textureId, int vertexCount) implements DrawCommand {}
    private record RunnableCommand(Runnable action) implements DrawCommand {}
}
