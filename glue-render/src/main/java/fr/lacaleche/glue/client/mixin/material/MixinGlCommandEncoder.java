package fr.lacaleche.glue.client.mixin.material;

import com.mojang.blaze3d.opengl.GlRenderPass;
import com.mojang.blaze3d.opengl.GlStateManager;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Binds Lumos' material G-buffer for an armed entity draw, at {@code trySetup} RETURN -- the last
 * point before the draw is issued.
 *
 * <p>This is deliberately the same seam Iris binds its own framebuffer at. Earlier bind points
 * (createRenderPass, setPipeline) are clobbered: Iris' {@code batchedentityrendering} suppresses
 * the createRenderPass framebuffer bind during its flush and manages the target itself around the
 * draw. Binding here, after all of that, is the bind that actually survives to the draw. An
 * {@code @Inject} (not a {@code @Redirect}) so it coexists with Iris' own trySetup injections.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public class MixinGlCommandEncoder {

    @Inject(method = "trySetup", at = @At("RETURN"))
    private void glue$bindGBuffer(GlRenderPass pass, Collection<String> uniforms,
                                  CallbackInfoReturnable<Boolean> cir) {
        int fbo = GBufferCapture.consumePendingRedirect();
        if (Boolean.TRUE.equals(cir.getReturnValue()) && fbo != 0) {
            GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        }
    }
}
