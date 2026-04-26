package fr.lacaleche.glue.client.shader;

import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class DeferredDrawQueue {

    private static boolean initialized = false;

    // Object pool to eliminate GC overhead
    private static final List<PooledCommand> commandPool = new ArrayList<>(256);
    private static int activeCommandCount = 0;

    public static void init() {
        if (initialized) return;
        initialized = true;
        WorldRenderEvents.LAST.register(context -> flush());
    }

    private static PooledCommand obtainCommand() {
        if (activeCommandCount >= commandPool.size()) {
            commandPool.add(new PooledCommand());
        }
        return commandPool.get(activeCommandCount++);
    }

    static void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = 0;
            cmd.mvp.set(mvp);
            cmd.vertexCount = vertexCount;
            ensureCapacity(cmd, vertices.length, 0, colors.length);
            System.arraycopy(vertices, 0, cmd.vertices, 0, vertices.length);
            System.arraycopy(colors, 0, cmd.colors, 0, colors.length);
        } else {
            GlDirectRenderer.drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    static void enqueueTextured(Matrix4f mvp, float[] vertices, float[] uvs,
                                float[] colors, int textureId, int vertexCount) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = 1;
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
            cmd.type = 2;
            cmd.action = action;
        } else {
            action.run();
        }
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
            if (cmd.type == 0) {
                GlDirectRenderer.drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount, true);
            } else if (cmd.type == 1) {
                GlDirectRenderer.drawTexturedQuad(cmd.mvp, cmd.vertices, cmd.uvs, cmd.colors, cmd.textureId, cmd.vertexCount, true);
            } else if (cmd.type == 2 && cmd.action != null) {
                cmd.action.run();
                cmd.action = null; // Clear reference to avoid memory leaks
            }
        }

        activeCommandCount = 0;
    }

    private static class PooledCommand {
        int type; // 0=Quad, 1=Textured, 2=Runnable
        final Matrix4f mvp = new Matrix4f();
        float[] vertices = new float[12]; // default quad 3 * 4
        float[] colors = new float[16];   // default quad 4 * 4
        float[] uvs = new float[8];       // default quad 2 * 4
        int textureId;
        int vertexCount;
        Runnable action;
    }
}
