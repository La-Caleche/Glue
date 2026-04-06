package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
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
        if (!RenderCompat.isIrisShaderEnabled()) return;
        ShadedBufferSource.postCompositeBlit();
    }
}

