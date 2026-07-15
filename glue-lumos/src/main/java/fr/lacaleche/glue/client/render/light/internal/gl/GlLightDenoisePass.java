package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

/**
 * Depth-guided (cross-bilateral) denoise of the accumulated HDR light buffer, run once
 * per axis. World-position weighting preserves geometry edges: a depth discontinuity is a
 * large position jump, so its samples drop out while a surface's interior smooths freely.
 *
 * <p>A true bilateral filter is not separable; running one horizontal then one vertical
 * pass is an approximation chosen for cost, and is sufficient to remove the stochastic
 * shadow/glass grain without softening edges perceptibly.
 */
@Environment(EnvType.CLIENT)
public final class GlLightDenoisePass {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");
    private static final float POSITION_SIGMA = 0.15f;

    private final GlLightResources resources;

    private int fboA = 0;
    private int texA = 0;
    private int fboB = 0;
    private int texB = 0;
    private int width = 0;
    private int height = 0;
    private boolean incompleteLogged = false;

    public GlLightDenoisePass(GlLightResources resources) {
        this.resources = resources;
    }

    /**
     * Runs the two-axis blur over {@code lightTexture}, guided by {@code guideDepthTexture},
     * and returns the denoised color texture id. Returns {@code lightTexture} unchanged when
     * the program cannot be resolved or the input is unavailable.
     */
    public int apply(int lightTexture, int guideDepthTexture, Matrix4f inverseViewProjection,
                     int width, int height) {
        int program = resources.program("glue_light_denoise",
                "light/deferred.vsh", "light/denoise.fsh");
        if (program == 0 || lightTexture <= 0) return lightTexture;

        SavedGlState state = SavedGlState.save();
        try {
            ensureSize(width, height);
            if (fboA == 0 || fboB == 0) return lightTexture;

            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glColorMask(true, true, true, true);
            GL11.glViewport(0, 0, width, height);

            resources.uniformMatrix(program, "InvViewProj", inverseViewProjection);
            resources.uniform1f(program, "PositionSigma", POSITION_SIGMA);
            resources.uniform1i(program, "LightTex", 0);
            resources.uniform1i(program, "GuideDepth", 1);

            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, guideDepthTexture);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboA);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture);
            resources.uniform2f(program, "Direction", 1f / width, 0f);
            resources.drawFullscreen(program);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fboB);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texA);
            resources.uniform2f(program, "Direction", 0f, 1f / height);
            resources.drawFullscreen(program);

            return texB;
        } finally {
            state.restore();
        }
    }

    private void ensureSize(int w, int h) {
        if (fboA != 0 && w == width && h == height) return;
        cleanup();
        width = w;
        height = h;

        texA = createTexture(w, h);
        fboA = createFbo(texA, "denoise scratch A");
        texB = createTexture(w, h);
        fboB = createFbo(texB, "denoise scratch B");
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
