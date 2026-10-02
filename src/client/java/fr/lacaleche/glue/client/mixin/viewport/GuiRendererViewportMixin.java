package fr.lacaleche.glue.client.mixin.viewport;

import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Restricts vanilla's deferred screen blur to the active game viewport. */
@Mixin(GuiRenderer.class)
public class GuiRendererViewportMixin {

    @Redirect(method = "draw", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;processBlurEffect()V"))
    private void glue$blurGameViewport(GameRenderer renderer) {
        GameViewportStage.processBlurEffect(renderer);
    }
}
