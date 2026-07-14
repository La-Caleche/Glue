package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/** Final display-space composite of accumulated illumination and scene color. */
public final class GlLightCompositePass {

    private static final float EXPOSURE = 1.0f;
    private final GlLightResources resources;

    public GlLightCompositePass(GlLightResources resources) {
        this.resources = resources;
    }

    public void render(int lightTexture, int sceneColor, int sceneDepth,
                       int materialColor, int materialDepth,
                       int destinationFramebuffer, int width, int height) {
        int program = resources.program("glue_light_composite",
                "light/deferred.vsh", "light/composite.fsh");
        if (program == 0 || lightTexture <= 0 || sceneColor <= 0) return;

        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destinationFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glViewport(0, 0, width, height);
            GL20.glUseProgram(program);
            setupState();

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightTexture);
            resources.uniform1i(program, "LightTex", 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColor);
            resources.uniform1i(program, "SceneTex", 1);

            boolean hasMaterial = materialColor > 0 && materialDepth > 0 && sceneDepth > 0;
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasMaterial ? materialColor : 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasMaterial ? materialDepth : 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasMaterial ? sceneDepth : 0);
            resources.uniform1i(program, "MaterialAlbedo", 2);
            resources.uniform1i(program, "MaterialDepth", 3);
            resources.uniform1i(program, "SceneDepth", 4);
            resources.uniform1i(program, "HasMaterial", hasMaterial ? 1 : 0);
            resources.uniform1f(program, "Exposure", EXPOSURE);
            resources.drawFullscreen(program);
        } finally {
            state.restore();
        }
    }

    private static void setupState() {
        GL11.glColorMask(true, true, true, true);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }
}
