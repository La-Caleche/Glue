package fr.lacaleche.glue.client.shader.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.*;

/**
 * Captures and restores a comprehensive snapshot of the OpenGL state
 * that Glue's shader operations may modify.
 *
 * <p>Used by {@link GlDirectRenderer} and
 * {@link fr.lacaleche.glue.client.shader.PostShaderHandle PostShaderHandle}
 * to guarantee clean state around low-level GL calls.</p>
 */
@Environment(EnvType.CLIENT)
public record SavedGlState(
        int program, int fbo, int vao,
        boolean blend, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
        boolean depth, boolean depthWrite, int depthFunc,
        boolean cull,
        boolean scissor,
        int activeTexture,
        int[] viewport,
        int tex0, int tex1, int tex2
) {
    public static SavedGlState save() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int tex0 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        int tex1 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        int tex2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL13.glActiveTexture(activeTexture);
        
        return new SavedGlState(
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                activeTexture,
                vp,
                tex0, tex1, tex2
        );
    }

    public void restore() {
        GL20.glUseProgram(program);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glBindVertexArray(vao);
        if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST);
        else GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(depthFunc);
        GL11.glDepthMask(depthWrite);
        if (blend) {
            GL11.glEnable(GL11.GL_BLEND);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        else GL11.glDisable(GL11.GL_CULL_FACE);
        if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
        else GL11.glDisable(GL11.GL_SCISSOR_TEST);
        
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex0);
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex1);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex2);
        
        GL13.glActiveTexture(activeTexture);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }
}
