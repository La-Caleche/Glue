package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A framebuffer that pairs a capture's colour texture with the scene's own depth texture, so a draw that
 * does not write depth is tested against the scene without a copy and leaves that depth untouched.
 *
 * <p><strong>Internal:</strong> owned by {@link fr.lacaleche.glue.client.shader.ShaderContext}.</p>
 */
@Environment(EnvType.CLIENT)
public final class SceneDepthCapture {

    private int framebufferId;

    /**
     * Attaches the two textures and returns the framebuffer's id; 0 when the driver refuses the pairing,
     * a depth format it cannot attach beside that colour texture. Attached anew on every call because a
     * resized target deletes its textures and GL may hand their ids to new ones.
     */
    public int attach(int colorTextureId, int depthTextureId) {
        RenderSystem.assertOnRenderThread();
        if (framebufferId == 0) {
            framebufferId = GL30.glGenFramebuffers();
        }
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebufferId);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, colorTextureId, 0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depthTextureId, 0);
        boolean complete = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        return complete ? framebufferId : 0;
    }

    public void cleanup() {
        if (framebufferId != 0) {
            GL30.glDeleteFramebuffers(framebufferId);
        }
        framebufferId = 0;
    }
}
