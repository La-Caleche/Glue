package fr.lacaleche.glue.client.utils;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;

public class FramebufferHelper {
    public static RenderTarget resizeOrCreate(RenderTarget framebuffer, int width, int height) {
        if (framebuffer == null) {
            framebuffer = new TextureTarget("SceneRenderer", width, height, true);
        } else if (framebuffer.width != width || framebuffer.height != height) {
            framebuffer.resize(width, height);
        }
        return framebuffer;
    }

    public static void clear(RenderTarget framebuffer, float r, float g, float b, float a) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                framebuffer.getColorTexture(),
                net.minecraft.util.ARGB.colorFromFloat(a, r, g, b),
                framebuffer.getDepthTexture(),
                1.0d);
    }

    public static int getColorTextureId(RenderTarget framebuffer) {
        if (framebuffer.getColorTexture() instanceof GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
    }

    /**
     * Gets the GL framebuffer object (FBO) ID for a render target.
     * <p>
     * Uses the same mechanism as Iris's {@code iris$bindFramebuffer()} — obtains the FBO
     * from the color texture's {@code GlTexture.getFbo()} method, which creates or retrieves
     * the FBO associated with the color+depth texture pair.
     * <p>
     * This is critical for Iris compatibility: Iris may redirect the main render target's
     * framebuffer, so we must use the SAME FBO that Iris uses when we draw raw GL.
     *
     * @param target The render target
     * @return The GL FBO ID, or -1 if the texture is not a GlTexture
     */
    public static int getFramebufferId(RenderTarget target) {
        if (target.getColorTexture() instanceof GlTexture glTexture) {
            GlDevice device = (GlDevice) RenderSystem.getDevice();
            return glTexture.getFbo(device.directStateAccess(), target.getDepthTexture());
        }
        return -1;
    }
}

