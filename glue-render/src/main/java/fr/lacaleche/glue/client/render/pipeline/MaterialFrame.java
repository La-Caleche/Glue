package fr.lacaleche.glue.client.render.pipeline;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
/**
 * Material textures produced for one {@link WorldRenderFrame}.
 *
 * <p>The color texture contains linear albedo in RGB and a packed normal in alpha.
 * The depth texture is captured from the same visible surface and frame.</p>
 */
@Environment(EnvType.CLIENT)
public record MaterialFrame(
        long frameSequence,
        String providerId,
        int colorTextureId,
        int depthTextureId,
        int width,
        int height
) {
    public MaterialFrame {
        if (frameSequence < 0) throw new IllegalArgumentException("frameSequence must be non-negative");
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("providerId must not be blank");
        }
        if (colorTextureId <= 0 || depthTextureId <= 0) {
            throw new IllegalArgumentException("material textures must be valid OpenGL names");
        }
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("material dimensions must be positive");
    }
}
