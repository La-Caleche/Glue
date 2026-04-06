package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Collection;

/**
 * Redirects custom shader draws to a private capture FBO when bypass + capture mode is active.
 * The capture FBO uses its own depth buffer (cleared to 1.0 = far plane), so the item
 * always passes depth test regardless of scene depth. Blending is disabled to store
 * raw fragment colors.
 */
@Mixin(GlCommandEncoder.class)
public class GlueDrawBufferFixMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-mixin");

    @Unique
    private static final Field BYPASS_FIELD;

    @Unique
    private static boolean logged = false;

    static {
        Field f = null;
        try {
            Class<?> cls = Class.forName("net.irisshaders.iris.vertices.ImmediateState");
            f = cls.getField("bypass");
        } catch (ClassNotFoundException | NoSuchFieldException e) {
            // Iris not present
        }
        BYPASS_FIELD = f;
    }

    @Inject(method = "trySetup", at = @At("RETURN"))
    private void glue$redirectToCaptureFbo(GlRenderPass glRenderPass,
                                            Collection<String> collection,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (BYPASS_FIELD == null) return;

        boolean bypass;
        try {
            bypass = (boolean) BYPASS_FIELD.get(null);
        } catch (IllegalAccessException e) {
            return;
        }

        if (!bypass || !ShadedBufferSource.isCapturing()) return;

        int captureFboId = ShadedBufferSource.getCaptureFboId();
        if (captureFboId <= 0) return;

        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        // Save the ORIGINAL FBO (Iris's entity rendering FBO) — it has valid scene depth
        int originalFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        // Query original FBO's depth texture for later blit comparison
        int origDepthType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int origDepthName = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

        if (origDepthType == GL11.GL_TEXTURE && origDepthName > 0) {
            ShadedBufferSource.setSceneDepthTextureId(origDepthName);
        }

        // Copy scene depth only once per frame — subsequent draws depth-test naturally
        if (!ShadedBufferSource.isDepthCopied()) {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFboId);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
            ShadedBufferSource.setDepthCopied(true);
        }

        // Now redirect draw to capture FBO
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, captureFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, w, h);

        if (!logged) {
            logged = true;
            LOGGER.info("[Glue-Mixin] Redirected draw to capture FBO {}, origFBO={}, sceneDepthTex={}",
                    captureFboId, originalFbo, origDepthName);
        }
    }
}
