package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * A no-op {@link VertexConsumer} that silently discards all vertex data.
 * Used by {@link fr.lacaleche.glue.client.shader.ShadedBufferSource ShadedBufferSource}
 * to swallow unsupported render types during capture.
 */
@Environment(EnvType.CLIENT)
public class NoopVertexConsumer implements VertexConsumer {

    public static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

    private NoopVertexConsumer() {
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }
}
