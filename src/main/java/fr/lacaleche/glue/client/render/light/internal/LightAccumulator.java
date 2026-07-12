package fr.lacaleche.glue.client.render.light.internal;

import fr.lacaleche.glue.client.shader.internal.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
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
 *   <li><b>Scene copy</b> &mdash; a snapshot of the scene color taken before the
 *       composite. The composite has to <em>read</em> the scene to tint the light
 *       by the surface it lands on, and it cannot sample the target it is writing
 *       to, so the scene is blitted aside first.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class LightAccumulator {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");

    private int fbo = 0;
    private int colorTex = 0;

    private int sceneFbo = 0;
    private int sceneTex = 0;

    private int width = 0;
    private int height = 0;
    private boolean incompleteLogged = false;

    /** Ensure the targets match the scene size and clear the accumulator. Call once per frame. */
    public void beginFrame(int w, int h) {
        SavedGlState state = SavedGlState.save();
        ensureSize(w, h);
        if (fbo != 0) {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL11.glViewport(0, 0, width, height);
            GL11.glClearColor(0f, 0f, 0f, 0f);
            GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
        }
        state.restore();
    }

    /**
     * Copy the scene color out of {@code srcFboId} so the composite can sample it.
     * Sampling and rendering to the same texture is undefined, hence the copy.
     */
    public void captureScene(int srcFboId) {
        if (sceneFbo == 0 || srcFboId <= 0) return;
        SavedGlState state = SavedGlState.save();
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFboId);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        state.restore();
    }

    private void ensureSize(int w, int h) {
        if (fbo != 0 && w == width && h == height) return;
        cleanup();
        width = w;
        height = h;

        colorTex = createTexture(w, h);
        fbo = createFbo(colorTex, "light accumulation");

        sceneTex = createTexture(w, h);
        sceneFbo = createFbo(sceneTex, "scene copy");
    }

    private static int createTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_CLAMP);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_CLAMP);
        return tex;
    }

    private int createFbo(int tex, String label) {
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE && !incompleteLogged) {
            incompleteLogged = true;
            LOGGER.error("[Glue] Light {} FBO incomplete: 0x{}", label, Integer.toHexString(status));
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

    /** GL color texture id holding the scene snapshot taken by {@link #captureScene}, or -1. */
    public int getSceneTextureId() {
        return sceneTex == 0 ? -1 : sceneTex;
    }

    public void cleanup() {
        if (fbo != 0) {
            GL30.glDeleteFramebuffers(fbo);
            fbo = 0;
        }
        if (sceneFbo != 0) {
            GL30.glDeleteFramebuffers(sceneFbo);
            sceneFbo = 0;
        }
        if (colorTex != 0) {
            GL11.glDeleteTextures(colorTex);
            colorTex = 0;
        }
        if (sceneTex != 0) {
            GL11.glDeleteTextures(sceneTex);
            sceneTex = 0;
        }
        width = height = 0;
    }
}
