package fr.lacaleche.glue.client.utils;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.ARGB;

public class FramebufferHelper {

    public static RenderTarget resizeOrCreate(RenderTarget framebuffer, int width, int height) {
        if (framebuffer == null) {
            return new TextureTarget("SceneRenderer", width, height, true);
        }
        if (framebuffer.width != width || framebuffer.height != height) {
            framebuffer.resize(width, height);
        }
        return framebuffer;
    }

    public static void clear(RenderTarget framebuffer, float r, float g, float b, float a) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                framebuffer.getColorTexture(),
                ARGB.colorFromFloat(a, r, g, b),
                framebuffer.getDepthTexture(),
                1.0d);
    }

    public static int getColorTextureId(RenderTarget framebuffer) {
        if (framebuffer.getColorTexture() instanceof GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
    }

    public static int getDepthTextureId(RenderTarget framebuffer) {
        if (framebuffer.getDepthTexture() instanceof GlTexture glTexture) {
            return glTexture.glId();
        }
        return -1;
    }

    public static int getFramebufferId(RenderTarget target) {
        if (target.getColorTexture() instanceof GlTexture glTexture) {
            GlDevice device = (GlDevice) RenderSystem.getDevice();
            return glTexture.getFbo(device.directStateAccess(), target.getDepthTexture());
        }
        return -1;
    }
}
