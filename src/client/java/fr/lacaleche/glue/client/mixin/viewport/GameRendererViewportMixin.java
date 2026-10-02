package fr.lacaleche.glue.client.mixin.viewport;

import com.llamalad7.mixinextras.sugar.Local;
import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets the world pass. It opens at the head of the frame rather than at {@code renderLevel} so
 * that the global settings uniform — updated first thing, and read by every shader as the screen size
 * — is filled with the viewport's dimensions rather than the window's. It closes where the frame turns
 * to the GUI, which draws into the real frame at full window size.
 */
@Mixin(GameRenderer.class)
public class GameRendererViewportMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void glue$beginGameViewport(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
        GameViewportStage.beginFrame(renderLevel);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void glue$endGameViewport(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo callback) {
        GameViewportStage.endWorld();
    }

    /**
     * Composites before Minecraft calls the HUD rather than from inside it. Screens such as Iris's
     * shader selector deliberately suppress {@link Gui#render}; the game viewport must remain visible
     * underneath those screens even though the HUD itself is hidden.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void glue$compositeGameViewport(DeltaTracker deltaTracker, boolean renderLevel,
                                            CallbackInfo callback, @Local GuiGraphics graphics) {
        GameViewportStage.composite(graphics);
    }
}
