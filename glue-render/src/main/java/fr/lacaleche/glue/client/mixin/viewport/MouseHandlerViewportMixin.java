package fr.lacaleche.glue.client.mixin.viewport;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Moves the mouse into the viewport's GUI space while it is active.
 *
 * <p>Every GUI-space consumer of the pointer — screen clicks, drags and hovers, command suggestions,
 * the coordinates a screen renders against — funnels through these two instance accessors. With the
 * HUD and screens laid out against the viewport, the pointer has to live in the same space, or every
 * click would land offset by the viewport's origin. The replacement converts straight from the raw
 * cursor position, so it returns the same coordinate whether it is polled at event time or from
 * inside a render scope that has narrowed the window to the viewport. The static overloads carry
 * movement deltas through the same physical-screen to full-framebuffer scale, without applying the
 * viewport origin.</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerViewportMixin {

    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"),
            cancellable = true)
    private void glue$viewportScaledX(Window window, CallbackInfoReturnable<Double> callback) {
        if (!GameViewportStage.confinesGui()) return;

        MouseHandler self = (MouseHandler) (Object) this;
        callback.setReturnValue(GameViewportStage.viewportGuiX(window, self.xpos()));
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;)D", at = @At("HEAD"),
            cancellable = true)
    private void glue$viewportScaledY(Window window, CallbackInfoReturnable<Double> callback) {
        if (!GameViewportStage.confinesGui()) return;

        MouseHandler self = (MouseHandler) (Object) this;
        callback.setReturnValue(GameViewportStage.viewportGuiY(window, self.ypos()));
    }

    @Inject(method = "getScaledXPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"),
            cancellable = true)
    private static void glue$viewportScaledDeltaX(Window window, double delta,
                                                   CallbackInfoReturnable<Double> callback) {
        if (!GameViewportStage.confinesGui()) return;

        callback.setReturnValue(GameViewportStage.viewportGuiDeltaX(window, delta));
    }

    @Inject(method = "getScaledYPos(Lcom/mojang/blaze3d/platform/Window;D)D", at = @At("HEAD"),
            cancellable = true)
    private static void glue$viewportScaledDeltaY(Window window, double delta,
                                                   CallbackInfoReturnable<Double> callback) {
        if (!GameViewportStage.confinesGui()) return;

        callback.setReturnValue(GameViewportStage.viewportGuiDeltaY(window, delta));
    }
}
