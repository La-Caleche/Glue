package fr.lacaleche.glue.client.shader;

import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Draw queue for raw GL rendering with Iris-aware dispatch.
 * <p>
 * Two rendering modes:
 * <ul>
 *   <li><b>Iris shaders active</b> — Defers draw commands to {@code WorldRenderEvents.LAST}
 *       to render after all world passes (terrain, entities, particles, clouds, weather).
 *       This prevents cloud occlusion and shadow pass ghost duplicates.</li>
 *   <li><b>Iris shaders inactive (vanilla)</b> — Draws immediately during block entity
 *       rendering. The vanilla frame graph pipeline has different framebuffer bindings
 *       at {@code LAST} event time, so deferred drawing would render to the wrong target.</li>
 * </ul>
 * <p>
 * Must be initialized once during client startup via {@link #init()}.
 */
@Environment(EnvType.CLIENT)
public class DeferredDrawQueue {

    private static final Logger LOGGER = LoggerFactory.getLogger("DeferredDrawQueue");
    private static final List<DrawCommand> pendingDraws = new ArrayList<>();
    private static int debugCounter = 0;
    private static boolean initialized = false;

    /**
     * Registers the WorldRenderEvents.LAST listener to flush deferred draws.
     * Call once during client mod initialization.
     */
    public static void init() {
        if (initialized) return;
        initialized = true;

        WorldRenderEvents.LAST.register(context -> flush());
    }

    /**
     * Submits a draw command. Behavior depends on Iris state:
     * <ul>
     *   <li>Iris shadow pass → silently skipped (prevents ghost duplicates)</li>
     *   <li>Iris shaders active → deferred to WorldRenderEvents.LAST</li>
     *   <li>Iris shaders inactive → drawn immediately</li>
     * </ul>
     */
    static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        // Skip shadow pass draws — they'd create ghost quads at the light's position
        if (RenderCompat.isRenderingShadowPass()) {
            if (debugCounter++ % 600 == 0) LOGGER.info("[DEBUG] Skipping shadow pass draw");
            return;
        }

        if (RenderCompat.isIrisShaderEnabled()) {
            // Defer: Iris manages framebuffers, and we need to draw after clouds
            if (debugCounter++ % 600 == 0) LOGGER.info("[DEBUG] Enqueuing deferred draw (Iris active), queue size: {}", pendingDraws.size());
            pendingDraws.add(new DrawCommand(new Matrix4f(mvp), vertices.clone(), colors.clone(), vertexCount));
        } else {
            // Immediate: vanilla frame graph has correct framebuffer bound during block entity rendering
            if (debugCounter++ % 600 == 0) LOGGER.info("[DEBUG] Immediate draw (vanilla)");
            GlDirectRenderer.drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    /**
     * Flushes all deferred draw commands. Only used when Iris shaders are active.
     * Depth test ON: at LAST event time, the depth buffer contains all world geometry
     * and is valid for occlusion testing. Depth writing is off (set in drawQuad),
     * so the depth buffer is read-only — quads behind world geometry are correctly hidden.
     */
    private static void flush() {
        if (pendingDraws.isEmpty()) return;

        if (debugCounter++ % 600 == 0) LOGGER.info("[DEBUG] Flushing {} deferred draws", pendingDraws.size());
        for (DrawCommand cmd : pendingDraws) {
            GlDirectRenderer.drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount, true);
        }
        pendingDraws.clear();
    }

    private record DrawCommand(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {}
}
