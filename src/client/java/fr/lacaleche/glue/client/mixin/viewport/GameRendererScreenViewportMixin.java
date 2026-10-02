package fr.lacaleche.glue.client.mixin.viewport;

import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Draws an open screen inside the game viewport while it is active. The screen laid itself out
 * against the viewport's dimensions; this carries that layout to the viewport's position on the
 * frame, with the window reporting the same dimensions for the duration.
 */
@Mixin(GameRenderer.class)
public class GameRendererScreenViewportMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/Screen;renderWithTooltip(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void glue$renderScreenInViewport(Screen screen, GuiGraphics graphics,
                                             int mouseX, int mouseY, float deltaTick) {
        GameViewportStage.beginScreen(graphics);
        try {
            screen.renderWithTooltip(graphics, mouseX, mouseY, deltaTick);
        } finally {
            GameViewportStage.endScreen(graphics);
        }
    }
}
