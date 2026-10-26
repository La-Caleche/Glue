package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import fr.lacaleche.glue.client.render.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * A copy of the scene depth a shader can sample while the live depth stays attached to the target it
 * draws into. The copy takes the source's internal format, since a depth blit refuses mismatched ones.
 *
 * <p><strong>Internal:</strong> owned by {@link fr.lacaleche.glue.client.shader.ShaderContext}.</p>
 */
@Environment(EnvType.CLIENT)
public final class SceneDepthCopy {

    private int textureId;
    private int width;
    private int height;
    private int internalFormat;
    private int readFbo;
    private int drawFbo;
    private @Nullable GpuTextureView view;
    private boolean copiedThisFrame;
    private boolean copied;

    /** Copies {@code sourceTextureId} the first time it is asked for in a frame; null when the copy failed. */
    public @Nullable GpuTextureView copy(int sourceTextureId) {
        RenderSystem.assertOnRenderThread();
        if (copiedThisFrame) return copied ? view : null;

        copiedThisFrame = true;
        copied = false;
        SavedGlState saved = SavedGlState.save();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTextureId);
            int sourceWidth = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int sourceHeight = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            int sourceFormat = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_INTERNAL_FORMAT);
            if (sourceWidth <= 0 || sourceHeight <= 0) return null;
            if (textureId == 0 || sourceWidth != width || sourceHeight != height || sourceFormat != internalFormat)
                allocate(sourceWidth, sourceHeight, sourceFormat);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, sourceTextureId, 0);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDepthMask(true);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, 0, 0);
            copied = true;
        } finally {
            saved.restore();
        }
        return view;
    }

    public void endFrame() {
        copiedThisFrame = false;
    }

    public void cleanup() {
        deleteTexture();
        if (readFbo != 0) GL30.glDeleteFramebuffers(readFbo);
        if (drawFbo != 0) GL30.glDeleteFramebuffers(drawFbo);
        readFbo = 0;
        drawFbo = 0;
        copiedThisFrame = false;
    }

    private void allocate(int newWidth, int newHeight, int newFormat) {
        deleteTexture();
        width = newWidth;
        height = newHeight;
        internalFormat = newFormat;

        textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
        boolean stencil = newFormat == GL30.GL_DEPTH24_STENCIL8 || newFormat == GL30.GL_DEPTH32F_STENCIL8;
        int type = newFormat == GL30.GL_DEPTH24_STENCIL8 ? GL30.GL_UNSIGNED_INT_24_8
                : newFormat == GL30.GL_DEPTH32F_STENCIL8 ? GL30.GL_FLOAT_32_UNSIGNED_INT_24_8_REV
                : GL11.GL_FLOAT;
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, newFormat, newWidth, newHeight, 0,
                stencil ? GL30.GL_DEPTH_STENCIL : GL11.GL_DEPTH_COMPONENT, type, (ByteBuffer) null);

        if (readFbo == 0) {
            readFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL11.glReadBuffer(GL11.GL_NONE);
        }
        if (drawFbo == 0) {
            drawFbo = GL30.glGenFramebuffers();
        }
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, textureId, 0);

        view = new CopyView(new CopyTexture(textureId, newWidth, newHeight));
    }

    private void deleteTexture() {
        // Vanilla's view count deletes the id once, through GlStateManager so its binding cache stays right.
        if (view != null) {
            view.close();
            view.texture().close();
        } else if (textureId != 0) {
            GL11.glDeleteTextures(textureId);
        }
        textureId = 0;
        view = null;
    }

    /** Wraps the copy for {@code RenderPass.bindSampler}; its filter and wrap modes are set once, in GL. */
    private static final class CopyTexture extends GlTexture {

        private CopyTexture(int id, int width, int height) {
            super(GpuTexture.USAGE_TEXTURE_BINDING, "glue scene depth copy", TextureFormat.DEPTH32,
                    width, height, 1, 1, id);
            this.modesDirty = false;
        }
    }

    private static final class CopyView extends GlTextureView {

        private CopyView(GlTexture texture) {
            super(texture, 0, 1);
        }
    }
}
