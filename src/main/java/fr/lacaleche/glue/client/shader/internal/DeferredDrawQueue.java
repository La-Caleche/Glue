package fr.lacaleche.glue.client.shader.internal;

import fr.lacaleche.glue.client.shader.ShaderContext;
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

    public static final DeferredDrawQueue INSTANCE = new DeferredDrawQueue();

    private final List<PooledCommand> commandPool = new ArrayList<>(256);
    private int activeCommandCount = 0;

    private DeferredDrawQueue() {
    }

    private static void ensureCapacity(PooledCommand cmd, int vLen, int uvLen, int cLen) {
        if (cmd.vertices.length < vLen) cmd.vertices = new float[vLen];
        if (cmd.uvs.length < uvLen) cmd.uvs = new float[uvLen];
        if (cmd.colors.length < cLen) cmd.colors = new float[cLen];
    }

    /**
     * Registers the flush callback with {@link WorldRenderEvents#LAST}.
     * Called once from {@code GlueClient.onInitializeClient()}.
     */
    public void register() {
        WorldRenderEvents.LAST.register(context -> flush());
    }

    public void enqueue(Matrix4f mvp, float[] vertices, float[] colors, int vertexCount) {
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
            ShaderContext.get().getRenderer().drawQuad(mvp, vertices, colors, vertexCount, true);
        }
    }

    public void enqueueTextured(Matrix4f mvp, float[] vertices, float[] uvs,
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
            ShaderContext.get().getRenderer().drawTexturedQuad(mvp, vertices, uvs, colors, textureId, vertexCount, true);
        }
    }

    public void defer(Runnable action) {
        if (RenderCompat.isRenderingShadowPass()) return;

        if (RenderCompat.isIrisShaderEnabled()) {
            PooledCommand cmd = obtainCommand();
            cmd.type = CommandType.RUNNABLE;
            cmd.action = action;
        } else {
            action.run();
        }
    }

    private PooledCommand obtainCommand() {
        if (activeCommandCount >= commandPool.size()) {
            commandPool.add(new PooledCommand());
        }
        return commandPool.get(activeCommandCount++);
    }

    private void flush() {
        if (activeCommandCount == 0) return;

        int count = activeCommandCount;
        try {
            for (int i = 0; i < count; i++) {
                PooledCommand cmd = commandPool.get(i);
                switch (cmd.type) {
                    case QUAD -> ShaderContext.get().getRenderer()
                            .drawQuad(cmd.mvp, cmd.vertices, cmd.colors, cmd.vertexCount, true);
                    case TEXTURED -> ShaderContext.get().getRenderer()
                            .drawTexturedQuad(cmd.mvp, cmd.vertices, cmd.uvs, cmd.colors, cmd.textureId, cmd.vertexCount, true);
                    case RUNNABLE -> {
                        if (cmd.action != null) {
                            cmd.action.run();
                            cmd.action = null;
                        }
                    }
                }
            }
        } finally {
            activeCommandCount = 0;
        }
    }

    private enum CommandType {QUAD, TEXTURED, RUNNABLE}

    private static class PooledCommand {
        final Matrix4f mvp = new Matrix4f();
        CommandType type;
        float[] vertices = new float[12];
        float[] colors = new float[16];
        float[] uvs = new float[8];
        int textureId;
        int vertexCount;
        Runnable action;
    }
}
