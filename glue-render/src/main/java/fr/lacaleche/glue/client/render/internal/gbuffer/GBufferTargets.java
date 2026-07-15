package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * A raw-GL multi-render-target framebuffer that shares Minecraft's main colour and depth
 * textures and adds Lumos' material attachments.
 *
 * <p>Blaze3D's {@code RenderPass} on 1.21.8 binds a single colour attachment, so this cannot
 * be a Blaze3D {@code RenderTarget}. Instead it is a plain GL FBO built by hand:
 * <ul>
 *   <li>{@code COLOR_ATTACHMENT0} = the vanilla main colour texture (so the ordinary lit scene
 *       is still produced by the same draw);</li>
 *   <li>{@code COLOR_ATTACHMENT1} = albedo (linear RGB) + packed normal (A), {@code RGBA16F};</li>
 *   <li>{@code COLOR_ATTACHMENT2} = material id, {@code RGBA8} (red channel = id/255: entity 2,
 *       particle 3; 0 where cleared);</li>
 *   <li>depth = the vanilla main depth texture (shared).</li>
 * </ul>
 * A geometry draw redirected here writes colour, material and depth together, so the material
 * data is depth-consistent with the scene by construction -- the property a separate second
 * draw could never guarantee. The extra textures are owned and destroyed here; the main colour
 * and depth are borrowed and never deleted.
 */
@Environment(EnvType.CLIENT)
public final class GBufferTargets {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/gbuffer");

    private int fbo;
    private int albedoNormalTex;
    private int materialIdTex;

    private int width;
    private int height;
    private int borrowedColor;
    private int borrowedDepth;
    private boolean incompleteLogged;

    /**
     * Ensures the FBO exists and is wired to {@code main}'s current colour/depth textures at its
     * current size. Returns false if the target could not be made complete.
     */
    public boolean ensure(RenderTarget main) {
        if (main == null) return false;
        int color = FramebufferHelper.getColorTextureId(main);
        int depth = FramebufferHelper.getDepthTextureId(main);
        if (color <= 0 || depth <= 0) return false;

        if (fbo != 0 && main.width == width && main.height == height
                && color == borrowedColor && depth == borrowedDepth) {
            return true;
        }

        rebuild(main.width, main.height, color, depth);
        return fbo != 0;
    }

    private void rebuild(int w, int h, int color, int depth) {
        destroyOwned();
        width = w;
        height = h;
        borrowedColor = color;
        borrowedDepth = depth;

        albedoNormalTex = createTexture(w, h, GL30.GL_RGBA16F, GL11.GL_FLOAT);
        materialIdTex = createTexture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);

        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        fbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL11.GL_TEXTURE_2D, albedoNormalTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                GL11.GL_TEXTURE_2D, materialIdTex, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, depth, 0);
        GL20.glDrawBuffers(new int[]{
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2});

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (!incompleteLogged) {
                incompleteLogged = true;
                LOGGER.error("[Glue] G-buffer FBO incomplete: 0x{}", Integer.toHexString(status));
            }
            destroyOwned();
        }
    }

    /** The MRT framebuffer id, or 0 if not ready. */
    public int framebufferId() {
        return fbo;
    }

    /** Albedo (RGB) + packed normal (A) attachment, or 0. */
    public int albedoNormalTextureId() {
        return albedoNormalTex;
    }

    /** Material-id attachment, or 0. */
    public int materialIdTextureId() {
        return materialIdTex;
    }

    /** Clears only the owned material attachments (1 and 2); the shared colour/depth are left
     *  to vanilla's own frame clear. Call at the start of a captured frame. */
    public void clearMaterialAttachments() {
        if (fbo == 0) return;
        int previous = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2});
        GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        GL20.glDrawBuffers(new int[]{
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2});
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);
    }

    private static int createTexture(int w, int h, int internalFormat, int type) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, w, h, 0,
                GL11.GL_RGBA, type, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private void destroyOwned() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (albedoNormalTex != 0) {
            GL11.glDeleteTextures(albedoNormalTex);
            albedoNormalTex = 0;
        }
        if (materialIdTex != 0) {
            GL11.glDeleteTextures(materialIdTex);
            materialIdTex = 0;
        }
    }

    public void cleanup() {
        destroyOwned();
        width = height = 0;
        borrowedColor = borrowedDepth = 0;
        incompleteLogged = false;
    }
}
