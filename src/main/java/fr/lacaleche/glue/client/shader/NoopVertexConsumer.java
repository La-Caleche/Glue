package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Silently discards all vertex data. Used to skip render layers
 * that cannot be routed through a custom shader pipeline.
 */
public class NoopVertexConsumer implements VertexConsumer {

    public static final NoopVertexConsumer INSTANCE = new NoopVertexConsumer();

    private NoopVertexConsumer() {}

    @Override public VertexConsumer addVertex(float x, float y, float z) { return this; }
    @Override public VertexConsumer setColor(int r, int g, int b, int a) { return this; }
    @Override public VertexConsumer setUv(float u, float v) { return this; }
    @Override public VertexConsumer setUv1(int u, int v) { return this; }
    @Override public VertexConsumer setUv2(int u, int v) { return this; }
    @Override public VertexConsumer setNormal(float x, float y, float z) { return this; }
}
