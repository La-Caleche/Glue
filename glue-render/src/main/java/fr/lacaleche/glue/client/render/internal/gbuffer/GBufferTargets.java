package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
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
 *   <li>{@code COLOR_ATTACHMENT2} = material id + owning depth, {@code RGBA8} (red channel =
 *       id/255, GBA = packed depth24; 0 where cleared);</li>
 *   <li>{@code COLOR_ATTACHMENT3} = surface material properties, {@code RGBA8} (R = roughness,
 *       G = metalness, B = dielectric F0, A = reserved; 0 where cleared);</li>
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
    private int materialPropsTex;

    private int width;
    private int height;
    private int borrowedColor;
    private int borrowedDepth;
    private boolean incompleteLogged;

    /**
     * Ensures the FBO exists and is wired to {@code main}'s current colour/depth textures at its
     * current size. Returns false if the target could not be made complete.
     *
     * @param depthOverride a GL depth texture to borrow instead of {@code main}'s own (an Iris
     *                      shaderpack frame's scene depth), or {@code 0} for the main depth
     */
    public boolean ensure(RenderTarget main, int depthOverride) {
        if (main == null) return false;
        int color = FramebufferHelper.getColorTextureId(main);
        int depth = depthOverride > 0 ? depthOverride : FramebufferHelper.getDepthTextureId(main);
        if (color <= 0 || depth <= 0) return false;

        if (fbo != 0 && main.width == width && main.height == height
                && color == borrowedColor && depth == borrowedDepth) {
            return true;
        }

        rebuild(main.width, main.height, color, depth);
        return fbo != 0;
    }

    private void rebuild(int w, int h, int color, int depth) {
        SavedGlState state = SavedGlState.save();
        try {
            destroyOwned();
            width = w;
            height = h;
            borrowedColor = color;
            borrowedDepth = depth;

            albedoNormalTex = createTexture(w, h, GL30.GL_RGBA16F, GL11.GL_FLOAT);
            materialIdTex = createTexture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);
            materialPropsTex = createTexture(w, h, GL11.GL_RGBA8, GL11.GL_UNSIGNED_BYTE);

            fbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, color, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                    GL11.GL_TEXTURE_2D, albedoNormalTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                    GL11.GL_TEXTURE_2D, materialIdTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT3,
                    GL11.GL_TEXTURE_2D, materialPropsTex, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depth, 0);
            GL20.glDrawBuffers(new int[]{
                    GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1,
                    GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});

            int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
            if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
                if (!incompleteLogged) {
                    incompleteLogged = true;
                    LOGGER.error("[Glue] G-buffer FBO incomplete: 0x{}", Integer.toHexString(status));
                }
                destroyOwned();
            }
        } finally {
            state.restore();
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

    /** Material-properties attachment (roughness/metalness/F0), or 0. */
    public int materialPropsTextureId() {
        return materialPropsTex;
    }

    /**
     * Opens the glass re-render: restricts the draw-buffer set to the material attachments (1 and 2),
     * leaving attachment 0 (the borrowed main colour) untouched -- vanilla's translucent pass already
     * blended the pane there. Depth stays the borrowed main depth, read-only (the glass pipeline masks
     * depth), so occluded panes fail its LEQUAL test and never overwrite the terrain behind them.
     *
     * <p>NOT wrapped in {@link SavedGlState}: its {@code restore()} re-applies the saved draw-buffer
     * set to the bound FBO, and the entity capture can leave THIS FBO bound, so it would immediately
     * clobber the {@code {NONE,1,2}} set below back to {@code {0,1,2}} -- letting the glass draw's
     * (unwritten) colour output leak into the main colour. The draw-buffer set is meant to persist for
     * the glass draws; only the bound FBO is saved/restored here.</p>
     */
    public void beginGlassPass() {
        if (fbo == 0) return;
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL20.glDrawBuffers(new int[]{
                GL11.GL_NONE, GL30.GL_COLOR_ATTACHMENT1,
                GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevDraw);
    }

    /** Restores the full four-attachment draw-buffer set after {@link #beginGlassPass()}, and the
     *  material attachments' blend enable the captured draws suppressed. Like {@link #beginGlassPass()},
     *  this must not restore draw buffers via SavedGlState. */
    public void endGlassPass() {
        if (fbo == 0) return;
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL20.glDrawBuffers(new int[]{
                GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1,
                GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevDraw);
        restoreMaterialBlend();
    }

    /**
     * Turns blending OFF for the material attachments (1..3) only, leaving attachment 0 -- the real
     * scene colour -- blending exactly as vanilla asked.
     *
     * <p>Required as soon as a blended draw is captured. The material channels carry packed data,
     * not colour: an alpha composite would mix the incoming id with whatever the pixel already held
     * and decode as an unrelated material (an entity's id 2 landing on a cleared pixel with a source
     * alpha near 0.5 resolves to 1 -- terrain), and would equally corrupt the packed normal and
     * owning depth.
     *
     * <p>Must be re-issued for EVERY redirected draw, which is why this is not paired with a
     * begin-call: Blaze3D applies a pipeline's blend with the NON-indexed {@code glEnable(GL_BLEND)}
     * (via {@code GlStateManager}'s cached {@code BooleanState}), and a non-indexed enable re-enables
     * blending on every draw buffer -- silently undoing this the next time any pipeline switches
     * blending on.
     */
    public void suppressMaterialBlend() {
        GL30.glDisablei(GL11.GL_BLEND, 1);
        GL30.glDisablei(GL11.GL_BLEND, 2);
        GL30.glDisablei(GL11.GL_BLEND, 3);
    }

    /**
     * Re-syncs the material attachments' blend enable with draw buffer 0's, undoing
     * {@link #suppressMaterialBlend()}. Per-attachment blend enable is context state that outlives
     * the FBO binding and is absent from {@code GlStateManager}'s cache, so nothing else would ever
     * put it back: left suppressed, it would silently disable blending on colortex1..3 for an Iris
     * shaderpack enabled later in the session.
     */
    public void restoreMaterialBlend() {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        for (int attachment = 1; attachment <= 3; attachment++) {
            if (blend) GL30.glEnablei(GL11.GL_BLEND, attachment);
            else GL30.glDisablei(GL11.GL_BLEND, attachment);
        }
    }

    /** Re-points the borrowed colour/depth at their current textures and clears only the owned
     *  material attachments (1 and 2); the shared colour/depth are left to vanilla's own frame
     *  clear. Call at the start of a captured frame. */
    public void clearMaterialAttachments() {
        if (fbo == 0) return;
        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            // Re-attach the borrowed main colour/depth every frame. They belong to vanilla (or to
            // Iris while a shaderpack was active) and can be deleted and recreated across a
            // shaderpack toggle or resize -- often reusing the same GL id, which defeats the
            // id-based cache in ensure(). Deleting a texture silently detaches it from this FBO,
            // leaving a dead attachment 0 that drops every redirected entity draw. Re-pointing at
            // the live ids here keeps the target valid regardless of what happened to them.
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                    GL11.GL_TEXTURE_2D, borrowedColor, 0);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, borrowedDepth, 0);
            GL20.glDrawBuffers(new int[]{
                    GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            GL20.glDrawBuffers(new int[]{
                    GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1,
                    GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
        } finally {
            state.restore();
        }
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
        if (materialPropsTex != 0) {
            GL11.glDeleteTextures(materialPropsTex);
            materialPropsTex = 0;
        }
    }

    public void cleanup() {
        destroyOwned();
        width = height = 0;
        borrowedColor = borrowedDepth = 0;
        incompleteLogged = false;
    }
}
