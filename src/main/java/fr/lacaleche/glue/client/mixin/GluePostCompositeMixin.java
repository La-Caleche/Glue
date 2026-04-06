package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
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
        if (RenderCompat.isIrisShaderEnabled()) {
            ShadedBufferSource.postCompositeBlit();
        }

        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int mainFbo = FramebufferHelper.getFramebufferId(mainTarget);
        GL20.glUseProgram(0);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
        GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);

        RenderEvents.POST_WORLD_RENDER.invoker().run();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFbo);
    }
}
