package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Owns the two off-screen buffers the deferred light pass needs.
 *
 * <ul>
 *   <li><b>Accumulation</b> &mdash; a single {@code GL_RGBA16F} color attachment
 *       (no depth) that per-light contributions additively blend into. HDR so
 *       values above 1.0 survive to be rolled off by the tonemapping composite
 *       instead of clipping to flat white. {@code FramebufferHelper} only makes
 *       RGBA8 targets, hence the raw GL here.</li>
 *   <li><b>Scene copy</b> &mdash; color and depth snapshots taken before the
 *       composite. The composite cannot sample attachments of the framebuffer it
 *       is writing to, so both are blitted aside first.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class LightAccumulator {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");

    private int fbo = 0;
    private int colorTex = 0;

    private int litFbo = 0;
    private int litTex = 0;

    private int sceneFbo = 0;
    private int sceneTex = 0;
    private int sceneDepthTex = 0;
    /** Scratch read-FBO wrapping the frame's depth texture for the depth half of the capture. */
    private int depthReadFbo = 0;

    private int width = 0;
    private int height = 0;
    private boolean incompleteLogged = false;

    /** Ensure the targets match the scene size and clear the accumulator. Call once per frame. */
    public boolean beginFrame(int w, int h) {
        if (w <= 0 || h <= 0) return false;
        SavedGlState state = SavedGlState.save();
        try {
            ensureSize(w, h);
            if (fbo == 0 || sceneFbo == 0 || litFbo == 0) return false;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, width, height);
            // glClear obeys the scissor box and the color mask: with either left over from the
            // caller, last frame's light would survive outside the box or in the masked channels.
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
            return true;
        } finally {
            state.restore();
        }
    }

    /**
     * Copy scene color out of {@code srcFboId} and scene depth out of {@code depthTexId} so the
     * composite can sample them. Sampling any attachment of the framebuffer being rendered to is
     * undefined. Depth is taken from the frame's authoritative depth <em>texture</em>, not from
     * {@code srcFboId}'s attachment: on an Iris shaderpack frame the framebuffer holding the scene
     * colour is a composite target with no depth attached.
     */
    public void captureScene(int srcFboId, int depthTexId) {
        if (sceneFbo == 0 || srcFboId <= 0) return;
        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFboId);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFbo);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL11.glDrawBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                    GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            if (depthTexId > 0) {
                if (depthReadFbo == 0) depthReadFbo = GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, depthReadFbo);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                        GL11.GL_TEXTURE_2D, depthTexId, 0);
                // Depth-only read FBO: completeness requires an explicit no-colour read buffer.
                GL11.glReadBuffer(GL11.GL_NONE);
                GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                        GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            }
        } finally {
            state.restore();
        }
    }

    private void ensureSize(int w, int h) {
        if (fbo != 0 && w == width && h == height) return;
        cleanup();
        width = w;
        height = h;

        colorTex = createTexture(w, h);
        fbo = createFbo(colorTex, "light accumulation");

        litTex = createTexture(w, h);
        litFbo = createFbo(litTex, "lit hdr");

        sceneTex = createTexture(w, h);
        sceneDepthTex = createDepthTexture(w, h);
        sceneFbo = createFbo(sceneTex, sceneDepthTex, "scene copy");
        if (fbo == 0 || sceneFbo == 0 || litFbo == 0) cleanup();
    }

    private static int createTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private static int createDepthTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, w, h, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private int createFbo(int tex, String label) {
        return createFbo(tex, 0, label);
    }

    private int createFbo(int tex, int depthTex, String label) {
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
        if (depthTex != 0) {
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, depthTex, 0);
        }

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            if (!incompleteLogged) {
                incompleteLogged = true;
                LOGGER.error("[Glue] Light {} FBO incomplete: 0x{}", label, Integer.toHexString(status));
            }
            GL30.glDeleteFramebuffers(id);
            return 0;
        }
        return id;
    }

    /** GL framebuffer name of the accumulation target, or -1 if unavailable. */
    public int getFramebufferId() {
        return fbo == 0 ? -1 : fbo;
    }

    /** GL color texture id holding the accumulated (HDR) light, or -1 if unavailable. */
    public int getColorTextureId() {
        return colorTex == 0 ? -1 : colorTex;
    }

    /** GL framebuffer name of the linear HDR lit-scene target (composite writes here), or -1. */
    public int getLitFramebufferId() {
        return litFbo == 0 ? -1 : litFbo;
    }

    /** GL color texture id holding the linear HDR lit scene (scene + Lumos light), or -1. */
    public int getLitTextureId() {
        return litTex == 0 ? -1 : litTex;
    }

    /** GL color texture id holding the scene snapshot taken by {@link #captureScene}, or -1. */
    public int getSceneTextureId() {
        return sceneTex == 0 ? -1 : sceneTex;
    }

    /** GL depth texture id captured alongside {@link #getSceneTextureId()}, or -1. */
    public int getSceneDepthTextureId() {
        return sceneDepthTex == 0 ? -1 : sceneDepthTex;
    }

    public void cleanup() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (litFbo != 0) {
            GL30.glDeleteFramebuffers(litFbo);
            litFbo = 0;
        }
        if (sceneFbo != 0) {
            GL30.glDeleteFramebuffers(sceneFbo);
            sceneFbo = 0;
        }
        if (depthReadFbo != 0) {
            GL30.glDeleteFramebuffers(depthReadFbo);
            depthReadFbo = 0;
        }
        if (colorTex != 0) {
            GL11.glDeleteTextures(colorTex);
            colorTex = 0;
        }
        if (litTex != 0) {
            GL11.glDeleteTextures(litTex);
            litTex = 0;
        }
        if (sceneTex != 0) {
            GL11.glDeleteTextures(sceneTex);
            sceneTex = 0;
        }
        if (sceneDepthTex != 0) {
            GL11.glDeleteTextures(sceneDepthTex);
            sceneDepthTex = 0;
        }
        width = height = 0;
        incompleteLogged = false;
    }
}
