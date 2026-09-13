package fr.lacaleche.glue.mcsx.client.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Composites the MCSX overlay after the HUD and before any open screen. */
@Mixin(GameRenderer.class)
public class GuiOverlayMixin {

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V",
            shift = At.Shift.AFTER))
    private void mcsx$renderOverlay(DeltaTracker deltaTracker, boolean renderLevel,
                                    CallbackInfo callback, @Local GuiGraphics graphics) {
        OverlayHost.render(graphics);
    }
}
