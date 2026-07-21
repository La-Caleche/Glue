package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

/**
 * Screen-space reflection for glass. Runs after the light composite has written the lit
 * scene to the main target: for every pane pixel it reflects the view ray, marches it
 * through the scene depth buffer, and alpha-blends the scene colour it hits back onto the
 * pane, Fresnel-weighted. Reflects the actual environment (including Lumos-lit surfaces),
 * which is what the additive point-light response could never do.
 *
 * <p>Holds no GL objects of its own: the shader program and fullscreen quad come from the
 * shared {@link GlLightResources}, and the scene colour/depth it samples are the copies the
 * {@link LightAccumulator} already maintains.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlGlassReflectionPass {

    private static final float STRENGTH = 0.65f;

    private final GlLightResources resources;

    public GlGlassReflectionPass(GlLightResources resources) {
        this.resources = resources;
    }

    /**
     * @param sceneColor  copy of the composited (lit) scene colour
     * @param sceneDepth  copy of the scene depth, sampled along the reflection ray
     * @param gbufferId   material-id attachment: pane pixels carry the GLASS id (4)
     */
    public void render(int sceneColor, int sceneDepth, int gbufferId, int gbufferAlbedo,
                       Matrix4f viewProjection, Matrix4f inverseViewProjection,
                       Vector3d cameraPos, float time,
                       int destinationFramebuffer, int width, int height) {
        if (sceneColor <= 0 || sceneDepth <= 0 || gbufferId <= 0) return;
        int program = resources.program("glue_glass_reflect",
                "light/deferred.vsh", "light/glass_reflect.fsh");
        if (program == 0) return;

        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, destinationFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glViewport(0, 0, width, height);
            GL20.glUseProgram(program);

            GL11.glColorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            // mix(scene, reflection, alpha) on colour; leave destination alpha untouched.
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ZERO, GL11.GL_ONE);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColor);
            resources.uniform1i(program, "SceneColor", 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE1);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepth);
            resources.uniform1i(program, "SceneDepth", 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, gbufferId);
            resources.uniform1i(program, "GBufferId", 2);
            resources.uniform1i(program, "HasGBuffer", 1);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, Math.max(gbufferAlbedo, 0));
            resources.uniform1i(program, "GBufferAlbedo", 3);

            resources.uniformMatrix(program, "InvViewProj", inverseViewProjection);
            resources.uniformMatrix(program, "ViewProj", viewProjection);
            resources.uniform2f(program, "TexelSize", 1f / width, 1f / height);
            resources.uniform1f(program, "Strength", STRENGTH);
            resources.uniform1f(program, "Time", time);
            resources.uniform3f(program, "CameraPos",
                    (float) cameraPos.x, (float) cameraPos.y, (float) cameraPos.z);

            resources.drawFullscreen(program);
        } finally {
            state.restore();
        }
    }
}
