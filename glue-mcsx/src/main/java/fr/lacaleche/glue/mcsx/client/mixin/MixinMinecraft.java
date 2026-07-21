package fr.lacaleche.glue.mcsx.client.mixin;

import fr.lacaleche.glue.mcsx.mui.UIManager;
import fr.lacaleche.glue.mcsx.viewport.DockPresent;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The embedding's two frame anchors: pane-bounds/cursor reconciliation at the very start of the
 * frame (so the whole frame sees one window size), and the full-resolution dock UI draw right
 * after the main target's blit — the last moment before the buffer swap.
 */
@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    @Final
    private DeltaTracker.Timer deltaTracker;

    @Inject(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getNanos()J", shift = Shift.AFTER))
    private void mcsx$beginFrame(boolean tick, CallbackInfo ci) {
        ViewportEmbedding.beginFrame((Minecraft) (Object) this);
    }

    @Inject(method = "runTick",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;blitToScreen()V",
                    shift = Shift.AFTER))
    private void mcsx$afterMainBlit(boolean tick, CallbackInfo ci) {
        float deltaTick = deltaTracker.getGameTimeDeltaTicks();
        if (UIManager.isInitialized()) {
            UIManager.getInstance().renderEmbeddedOverlay(deltaTick);
        }
        DockPresent.drawFullRes((Minecraft) (Object) this, deltaTick);
    }
}
