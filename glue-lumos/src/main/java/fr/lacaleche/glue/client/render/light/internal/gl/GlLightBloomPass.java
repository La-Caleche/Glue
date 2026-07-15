package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Quarter-resolution bloom built from the HDR light buffer. A bright-pass downsample feeds
 * a separable Gaussian blur run twice with a growing step, giving a wide soft glow around
 * brightly-lit areas for a fraction of a full-resolution pass. Because it reads Lumos' light
 * contribution rather than the final image, it glows only what Lumos lit -- never vanilla's
 * own bright pixels.
 */
@Environment(EnvType.CLIENT)
public final class GlLightBloomPass {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");
    // Bright-pass cutoff on the VISIBLE lit brightness (linear). Only pixels brighter than
    // this glow, so the bloom tracks genuine highlights instead of every lit surface.
    private static final float THRESHOLD = 0.8f;

    private final GlLightResources resources;

    private int fboA = 0;
    private int texA = 0;
    private int fboB = 0;
    private int texB = 0;
    private int width = 0;
    private int height = 0;
    private boolean incompleteLogged = false;

    public GlLightBloomPass(GlLightResources resources) {
        this.resources = resources;
    }

    /**
     * Produces the blurred bloom texture from {@code lightTexture} (full-resolution HDR
     * light). Returns the bloom color texture id at quarter resolution, or 0 if unavailable.
     */
    public int apply(int lightTexture, int fullWidth, int fullHeight) {
        if (lightTexture <= 0) return 0;
        int bright = resources.program("glue_bloom_bright",
                "light/deferred.vsh", "light/bloom_bright.fsh");
        int blur = resources.program("glue_bloom_blur",
                "light/deferred.vsh", "light/bloom_blur.fsh");
        if (bright == 0 || blur == 0) return 0;

        int bw = Math.max(1, fullWidth / 4);
        int bh = Math.max(1, fullHeight / 4);

        SavedGlState state = SavedGlState.save();
        try {
            ensureSize(bw, bh);
            if (fboA == 0 || fboB == 0) return 0;

            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glViewport(0, 0, bw, bh);

            GL20.glUseProgram(bright);
            resources.uniform1i(bright, "LightTex", 0);
            resources.uniform2f(bright, "SrcTexel", 1f / fullWidth, 1f / fullHeight);
            resources.uniform1f(bright, "Threshold", THRESHOLD);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture);
            resources.drawFullscreen(bright);

            GL20.glUseProgram(blur);
            resources.uniform1i(blur, "Source", 0);
            // Two iterations, radius growing, for a wide glow. texA always holds the result.
            blurAxis(blur, fboB, texA, 1f / bw, 0f);
            blurAxis(blur, fboA, texB, 0f, 1f / bh);
            blurAxis(blur, fboB, texA, 2f / bw, 0f);
            blurAxis(blur, fboA, texB, 0f, 2f / bh);
            return texA;
        } finally {
            state.restore();
        }
    }

    private void blurAxis(int program, int targetFbo, int sourceTex, float dx, float dy) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFbo);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTex);
        resources.uniform2f(program, "Direction", dx, dy);
        resources.drawFullscreen(program);
    }

    private void ensureSize(int w, int h) {
        if (fboA != 0 && w == width && h == height) return;
        cleanup();
        width = w;
        height = h;
        texA = createTexture(w, h);
        fboA = createFbo(texA, "bloom A");
        texB = createTexture(w, h);
        fboB = createFbo(texB, "bloom B");
        if (fboA == 0 || fboB == 0) cleanup();
    }

    private static int createTexture(int w, int h) {
        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA16F, w, h, 0,
                GL11.GL_RGBA, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        return tex;
    }

    private int createFbo(int tex, String label) {
        int id = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, id);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, tex, 0);
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

    public void cleanup() {
        if (fboA != 0) {
            GL30.glDeleteFramebuffers(fboA);
            fboA = 0;
        }
        if (fboB != 0) {
            GL30.glDeleteFramebuffers(fboB);
            fboB = 0;
        }
        if (texA != 0) {
            GL11.glDeleteTextures(texA);
            texA = 0;
        }
        if (texB != 0) {
            GL11.glDeleteTextures(texB);
            texB = 0;
        }
        width = height = 0;
        incompleteLogged = false;
    }
}
