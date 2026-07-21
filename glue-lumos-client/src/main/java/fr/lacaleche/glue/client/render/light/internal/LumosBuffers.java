package fr.lacaleche.glue.client.render.light.internal;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.MultiBufferSource;

/**
 * The buffer source Lumos' own re-renders draw through — shadow bakes, the per-frame entity
 * shadow maps, and the material scene re-renders.
 *
 * <p>Deliberately NOT {@code renderBuffers().bufferSource()}: with an Iris shaderpack active,
 * Iris replaces that source with its batching one, which defers the draws past the re-render's
 * capture window — the shadow map stays empty, and the deferred flush later replays Lumos'
 * light-relative geometry into state the rest of the frame depends on (observed as every GUI
 * item model rendering black). A private immediate source flushes at {@code endBatch},
 * unconditionally, through the plain RenderType draw path the capture redirects are armed
 * around.</p>
 */
@Environment(EnvType.CLIENT)
public final class LumosBuffers {

    private static ByteBufferBuilder builder;
    private static MultiBufferSource.BufferSource source;

    private LumosBuffers() {
    }

    public static MultiBufferSource.BufferSource source() {
        if (source == null) {
            builder = new ByteBufferBuilder(0x40000);
            source = MultiBufferSource.immediate(builder);
        }
        return source;
    }

    public static void cleanup() {
        if (builder != null) {
            builder.close();
            builder = null;
            source = null;
        }
    }
}
