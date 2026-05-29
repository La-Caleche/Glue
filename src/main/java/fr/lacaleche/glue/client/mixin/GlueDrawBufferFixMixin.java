package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlRenderPass;
import fr.lacaleche.glue.client.shader.ShaderContext;
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

import java.util.Collection;

@Mixin(GlCommandEncoder.class)
public class GlueDrawBufferFixMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger("glue/mixin");

    @Unique
    private static boolean logged = false;

    @Inject(method = "trySetup", at = @At("RETURN"))
    private void glue$redirectToCaptureFbo(GlRenderPass glRenderPass,
                                           Collection<String> collection,
                                           CallbackInfoReturnable<Boolean> cir) {
        ShaderContext ctx = ShaderContext.get();
        if (ctx.isCapturing()) {
            int captureFboId = ctx.getCaptureFboId();
            if (captureFboId > 0) {
                redirectToFbo(captureFboId, ctx.shouldCopyDepth());
            }
            return;
        }

        if (ctx.isIsolatedCapturing()) {
            int isolatedFboId = ctx.getIsolatedFboId();
            if (isolatedFboId > 0) {
                redirectToFbo(isolatedFboId, false);
            }
        }
    }

    @Unique
    private void redirectToFbo(int targetFboId, boolean copyDepth) {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        int originalFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        if (copyDepth) {
            int origDepthType = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
            int origDepthName = GL30.glGetFramebufferAttachmentParameteri(
                    GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

            if (origDepthType == GL11.GL_TEXTURE && origDepthName > 0) {
                ShaderContext.get().setSceneDepthTextureId(origDepthName);
            }

            if (!ShaderContext.get().hasDepthBeenCopied()) {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, originalFbo);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, targetFboId);
                GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
                ShaderContext.get().markDepthCopied();
            }
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, targetFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, w, h);

        if (!logged) {
            logged = true;
            LOGGER.info("[Glue-Mixin] Redirected draw to FBO {}, origFBO={}", targetFboId, originalFbo);
        }
    }
}
