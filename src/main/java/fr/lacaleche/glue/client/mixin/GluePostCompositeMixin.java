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
 * Injects AFTER GameRenderer.renderLevel() returns.
 * At this point, Iris has finished ALL compositing.
 * This is the correct time to blit our captured shader output.
 */
@Mixin(GameRenderer.class)
public class GluePostCompositeMixin {

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
                    shift = At.Shift.AFTER
            )
    )
    private void glue$afterRenderLevel(DeltaTracker deltaTracker, boolean bl, CallbackInfo ci) {
        if (!RenderCompat.isIrisShaderEnabled()) return;
        ShadedBufferSource.postCompositeBlit();
    }
}
