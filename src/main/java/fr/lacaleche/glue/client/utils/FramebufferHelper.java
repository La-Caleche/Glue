package fr.lacaleche.glue.client.utils;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;

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
        com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                framebuffer.getColorTexture(),
                net.minecraft.util.ARGB.colorFromFloat(a, r, g, b),
                framebuffer.getDepthTexture(),
                1.0d);
    }

    public static int getColorTextureId(RenderTarget framebuffer) {
        if (framebuffer.getColorTexture() instanceof com.mojang.blaze3d.opengl.GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
    }
}
