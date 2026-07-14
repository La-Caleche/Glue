package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.events.DebugEvents;
import fr.lacaleche.glue.client.events.RenderEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {

    @Inject(method = "renderDebugOverlay", at = @At("HEAD"))
    private void glue$renderDebugOverlay(GuiGraphics context, DeltaTracker tickCounter, CallbackInfo ci) {
        RenderEvents.RENDER_HUD.invoker().accept(context);
    }

    @Inject(method = {"render"}, at = {@At("RETURN")})
    public void glue$render(GuiGraphics guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        float tickDelta = deltaTracker.getGameTimeDeltaTicks();
        int screenWidth = guiGraphics.guiWidth();
        int screenHeight = guiGraphics.guiHeight();
        RenderEvents.MAIN_RENDER.invoker().accept(guiGraphics, tickDelta, screenWidth, screenHeight);
        DebugEvents.GUI_DEBUG_LAYERS.invoker().onRenderGuiDebug(guiGraphics, tickDelta,
                screenWidth, screenHeight);
    }

}
