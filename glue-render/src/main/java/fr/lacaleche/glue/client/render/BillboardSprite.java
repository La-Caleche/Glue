package fr.lacaleche.glue.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Emits a camera-facing textured quad &mdash; a billboard &mdash; into a {@link VertexConsumer}.
 *
 * <p>Reusable and allocation-free per quad: construct once, orient it with {@link #facingCamera}
 * once per frame, then call {@link #emit} for each sprite. The corners are written in the same
 * winding and UV layout Minecraft uses for its own sprites, so it drops into any render type
 * (vanilla or a {@link fr.lacaleche.glue.client.shader.GluePipeline} buffer) unchanged.</p>
 *
 * <p>Positions are given in the {@code pose} matrix's own space: pass the block-entity or
 * emitter pose and a centre offset, and the quad is built there without touching the
 * {@code PoseStack}.</p>
 */
public final class BillboardSprite {

    private static final float[][] CORNER_OFFSETS = {{-1f, -1f}, {1f, -1f}, {1f, 1f}, {-1f, 1f}};
    private static final float[][] CORNER_UVS = {{0f, 1f}, {1f, 1f}, {1f, 0f}, {0f, 0f}};

    private final Quaternionf orientation = new Quaternionf();
    private final Vector3f corner = new Vector3f();
    private final Vector3f normal = new Vector3f(0f, 0f, 1f);

    /** Orients every subsequently emitted quad to face this camera. Call once per frame. */
    public BillboardSprite facingCamera(Camera camera) {
        orientation.set(camera.rotation());
        normal.set(0f, 0f, 1f).rotate(orientation);
        return this;
    }

    /** Emits a camera-facing quad centred at {@code (cx, cy, cz)} in {@code pose} space. */
    public void emit(VertexConsumer consumer, Matrix4f pose,
                     float cx, float cy, float cz, float halfWidth, float halfHeight,
                     int r, int g, int b, int a, int packedLight) {
        emit(consumer, pose, cx, cy, cz, halfWidth, halfHeight, 0f, r, g, b, a, packedLight);
    }

    /** As {@link #emit}, spinning the quad by {@code roll} radians around the view axis. */
    public void emit(VertexConsumer consumer, Matrix4f pose,
                     float cx, float cy, float cz, float halfWidth, float halfHeight, float roll,
                     int r, int g, int b, int a, int packedLight) {
        float sin = roll == 0f ? 0f : (float) Math.sin(roll);
        float cos = roll == 0f ? 1f : (float) Math.cos(roll);
        for (int i = 0; i < 4; i++) {
            float ox = CORNER_OFFSETS[i][0] * halfWidth;
            float oy = CORNER_OFFSETS[i][1] * halfHeight;
            corner.set(ox * cos - oy * sin, ox * sin + oy * cos, 0f)
                    .rotate(orientation)
                    .add(cx, cy, cz);
            consumer.addVertex(pose, corner.x, corner.y, corner.z)
                    .setColor(r, g, b, a)
                    .setUv(CORNER_UVS[i][0], CORNER_UVS[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(normal.x, normal.y, normal.z);
        }
    }
}
