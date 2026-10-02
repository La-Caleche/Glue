package fr.lacaleche.glue.client.mixin.viewport;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Stands the game viewport's offscreen target in for the main render target across the world pass.
 * Every consumer resolves the target through this accessor per use, which is what lets the world land
 * in the viewport while the GUI that follows still draws into the real frame.
 */
@Mixin(Minecraft.class)
public class MinecraftViewportMixin {

    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void glue$gameViewportTarget(CallbackInfoReturnable<RenderTarget> callback) {
        RenderTarget override = GameViewportStage.renderTargetOverride();
        if (override != null) callback.setReturnValue(override);
    }
}
