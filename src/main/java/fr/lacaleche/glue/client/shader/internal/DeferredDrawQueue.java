package fr.lacaleche.glue.client.shader.internal;

import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Batches raw GL draw commands and deferred actions for replay after
 * Iris world compositing.
 *
 * <p>When Iris is active, all draw calls are pooled and flushed during
 * {@link net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents#LAST LAST}.
 * When vanilla is active, draws execute immediately.</p>
 *
 * <p><strong>Internal:</strong> consumers should use
 * {@link fr.lacaleche.glue.client.shader.GluePipeline GluePipeline} or
 * {@link fr.lacaleche.glue.client.shader.ShadedBufferSource ShadedBufferSource}
 * instead.</p>
 */
@Environment(EnvType.CLIENT)
public class DeferredDrawQueue {

    private static final List<PooledCommand> commandPool = new ArrayList<>(256);

    private static boolean initialized = false;
    private static int activeCommandCount = 0;

    public static void init() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.LAST.register(context -> flush());
    }

    public static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = CommandType.QUAD;
            cmd.mvp.set(mvp);
            cmd.vertexCount = vertexCount;
            ensureCapacity(cmd, vertices.length, 0, colors.length);
            System.arraycopy(vertices, 0, cmd.vertices, 0, vertices.length);
            System.arraycopy(colors, 0, cmd.colors, 0, colors.length);
        } else {
            GlDirectRenderer.drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    public static void enqueueTextured(Matrix4f mvp, float[] vertices, float[] uvs,
                                       float[] colors, int textureId, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = CommandType.TEXTURED;
            cmd.mvp.set(mvp);
            cmd.textureId = textureId;
            cmd.vertexCount = vertexCount;
            ensureCapacity(cmd, vertices.length, uvs.length, colors.length);
            System.arraycopy(vertices, 0, cmd.vertices, 0, vertices.length);
            System.arraycopy(uvs, 0, cmd.uvs, 0, uvs.length);
            System.arraycopy(colors, 0, cmd.colors, 0, colors.length);
        } else {
            GlDirectRenderer.drawTexturedQuad(mvp, vertices, uvs, colors, textureId, vertexCount, true);
        }
    }

    public static void defer(Runnable action) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = CommandType.RUNNABLE;
            cmd.action = action;
        } else {
            action.run();
        }
    }

    private static PooledCommand obtainCommand() {
        if (activeCommandCount >= commandPool.size()) {
            commandPool.add(new PooledCommand());
        }
        return commandPool.get(activeCommandCount++);
    }

    private static void ensureCapacity(PooledCommand cmd, int vLen, int uvLen, int cLen) {
        if (cmd.vertices.length < vLen) cmd.vertices = new float[vLen];
        if (cmd.uvs.length < uvLen) cmd.uvs = new float[uvLen];
        if (cmd.colors.length < cLen) cmd.colors = new float[cLen];
    }

    private static void flush() {
        if (activeCommandCount == 0) return;

        for (int i = 0; i < activeCommandCount; i++) {
            PooledCommand cmd = commandPool.get(i);
            if (cmd.type == CommandType.QUAD) {
                GlDirectRenderer.drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount, true);
            } else if (cmd.type == CommandType.TEXTURED) {
                GlDirectRenderer.drawTexturedQuad(cmd.mvp, cmd.vertices, cmd.uvs, cmd.colors, cmd.textureId, cmd.vertexCount, true);
            } else if (cmd.type == CommandType.RUNNABLE && cmd.action != null) {
                cmd.action.run();
                cmd.action = null; // Clear reference to avoid memory leaks
            }
        }

        activeCommandCount = 0;
    }

    private enum CommandType {QUAD, TEXTURED, RUNNABLE}

    private static class PooledCommand {
        final Matrix4f mvp = new Matrix4f();
        CommandType type;
        float[] vertices = new float[12]; // default quad 3 * 4
        float[] colors = new float[16];   // default quad 4 * 4
        float[] uvs = new float[8];       // default quad 2 * 4
        int textureId;
        int vertexCount;
        Runnable action;
    }
}
