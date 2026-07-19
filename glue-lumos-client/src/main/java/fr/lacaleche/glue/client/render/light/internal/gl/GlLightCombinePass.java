package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Final resolve of the deferred light pipeline: adds bloom to the linear HDR lit scene,
 * tonemaps, and writes the sRGB result to the main target. Reading the bloom from the lit
 * buffer's visible brightness (built by {@link GlLightBloomPass}) is what makes the glow
 * track actual bright pixels rather than the raw light field.
 */
@Environment(EnvType.CLIENT)
public final class GlLightCombinePass {

    private static final float BLOOM_STRENGTH = 0.5f;

    private final GlLightResources resources;

    public GlLightCombinePass(GlLightResources resources) {
        this.resources = resources;
    }

    public void render(int litTexture, int bloomTexture,
                       int destinationFramebuffer, int width, int height) {
        if (litTexture <= 0) return;
        int program = resources.program("glue_light_combine",
                "light/deferred.vsh", "light/combine.fsh");
        if (program == 0) return;

        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destinationFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glViewport(0, 0, width, height);
            GL20.glUseProgram(program);

            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, litTexture);
            resources.uniform1i(program, "LitTex", 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, bloomTexture > 0 ? bloomTexture : 0);
            resources.uniform1i(program, "BloomTex", 1);
            resources.uniform1f(program, "BloomStrength", bloomTexture > 0 ? BLOOM_STRENGTH : 0f);

            resources.drawFullscreen(program);
        } finally {
            state.restore();
        }
    }
}
