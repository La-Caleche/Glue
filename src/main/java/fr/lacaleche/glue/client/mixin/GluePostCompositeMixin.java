package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects inside GameRenderer.renderLevel() AFTER levelRenderer.renderLevel() returns
 * but BEFORE MC clears the depth texture to 1.0.
 *
 * At this point:
 *   - Iris has rendered ALL geometry INCLUDING the hand
 *   - Iris composite + final pass have completed
 *   - Depth texture 3 still contains scene + hand depth
 *   - MC has NOT yet cleared the depth (that happens at the next line)
 *
 * After the blit, fires POST_WORLD_RENDER so post-process effects (blur, grayscale etc.)
 * can be applied to the complete frame INCLUDING custom shader content.
 */
@Mixin(GameRenderer.class)
public class GluePostCompositeMixin {

    @Inject(
            method = "renderLevel",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/LevelRenderer;renderLevel(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/Camera;Lorg/joml/Matrix4f;Lorg/joml/Matrix4f;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
                    shift = At.Shift.AFTER
            )
    )
    private void glue$afterLevelRenderBeforeDepthClear(DeltaTracker deltaTracker, CallbackInfo ci) {
        // Iris path: blit custom shader output
        if (RenderCompat.isIrisShaderEnabled()) {
            ShadedBufferSource.postCompositeBlit();
        }

        // Clean up GL state so PostChain has a sane environment
        // (Iris unbinds all textures and may leave a custom program active)
        var mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int mainFbo = fr.lacaleche.glue.client.utils.FramebufferHelper.getFramebufferId(mainTarget);
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);

        // Fire event for post-process effects (blur, grayscale, etc.)
        RenderEvents.POST_WORLD_RENDER.invoker().run();

        // Re-bind main target so MC's subsequent code finds it in the right state
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
    }
}

