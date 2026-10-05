package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.ui.UiTextureView;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

/**
 * A greyscale preview of a depth texture, near bright and far dark, stretched over the depths the
 * frame holds. The depth is read back synchronously, which stalls the frame while it is captured.
 */
final class DepthPreview {

    private static final int SIZE = 256;

    private int texture = -1;
    private int sourceWidth;
    private int sourceHeight;

    void capture(int depthTexture, int width, int height) {
        if (this.texture == -1) {
            this.texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            resetUnpack();
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, SIZE, SIZE, 0, GL11.GL_RGBA,
                    GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        }

        GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);
        FloatBuffer depths = MemoryUtil.memAllocFloat(width * height);
        ByteBuffer pixels = MemoryUtil.memAlloc(SIZE * SIZE * 4);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, depthTexture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depths);
            float[] samples = new float[SIZE * SIZE];
            float min = 1.0F;
            float max = 0.0F;
            for (int y = 0; y < SIZE; y++) {
                int row = y * height / SIZE;
                for (int x = 0; x < SIZE; x++) {
                    float depth = depths.get(row * width + x * width / SIZE);
                    samples[y * SIZE + x] = depth;
                    if (depth < 1.0F) {
                        min = Math.min(min, depth);
                        max = Math.max(max, depth);
                    }
                }
            }
            if (max <= min) {
                min = 0.0F;
                max = 1.0F;
            }

            float range = max - min;
            for (int i = 0; i < samples.length; i++) {
                byte shade = samples[i] < 1.0F ? (byte) ((1.0F - (samples[i] - min) / range) * 255.0F) : 0;
                pixels.put(i * 4, shade).put(i * 4 + 1, shade).put(i * 4 + 2, shade).put(i * 4 + 3, (byte) 255);
            }
            resetUnpack();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, this.texture);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, SIZE, SIZE, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        } finally {
            MemoryUtil.memFree(depths);
            MemoryUtil.memFree(pixels);
        }
        this.sourceWidth = width;
        this.sourceHeight = height;
    }

    /** The last preview, sized as its source so a view keeps the frame's aspect, or null before one. */
    UiTextureView.@Nullable Texture texture() {
        if (this.texture == -1 || this.sourceWidth == 0) return null;

        return new UiTextureView.Texture(this.texture, this.sourceWidth, this.sourceHeight, true);
    }

    void free() {
        if (this.texture != -1) GL11.glDeleteTextures(this.texture);
        this.texture = -1;
        this.sourceWidth = 0;
        this.sourceHeight = 0;
    }

    private static void resetUnpack() {
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
    }
}
