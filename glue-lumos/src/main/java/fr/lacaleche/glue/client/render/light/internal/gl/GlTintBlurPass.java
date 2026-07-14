package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Separable blur applied once when a stained-glass shadow map is baked. */
public final class GlTintBlurPass {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");
    private static final float STRIDE = 1.0f;

    private final GlLightResources resources;
    private int scratchFramebuffer;
    private int scratchTexture;
    private int scratchSize;

    public GlTintBlurPass(GlLightResources resources) {
        this.resources = resources;
    }

    public void render(int tintFramebuffer, int tintTexture, int size) {
        int program = resources.program("glue_light_tint_blur",
                "light/deferred.vsh", "light/tint_blur.fsh");
        if (program == 0 || tintFramebuffer <= 0 || tintTexture <= 0 || size <= 0) return;

        SavedGlState state = SavedGlState.save();
        try {
            ensureScratch(size);
            if (scratchFramebuffer == 0) return;
            GL20.glUseProgram(program);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glViewport(0, 0, size, size);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, tintTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

            float step = STRIDE / size;
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            resources.uniform1i(program, "Source", 0);
            resources.uniform2f(program, "Direction", step, 0f);
            resources.drawFullscreen(program);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tintFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchTexture);
            resources.uniform2f(program, "Direction", 0f, step);
            resources.drawFullscreen(program);
        } finally {
            state.restore();
        }
    }

    public void cleanup() {
        if (scratchFramebuffer != 0) GL30.glDeleteFramebuffers(scratchFramebuffer);
        if (scratchTexture != 0) GL11.glDeleteTextures(scratchTexture);
        scratchFramebuffer = scratchTexture = scratchSize = 0;
    }

    private void ensureScratch(int size) {
        if (scratchFramebuffer != 0 && scratchSize == size) return;
        cleanup();
        scratchSize = size;
        scratchTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchTexture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        scratchFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, scratchTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("[Glue] Tint blur scratch FBO incomplete: 0x{}", Integer.toHexString(status));
            cleanup();
        }
    }
}
