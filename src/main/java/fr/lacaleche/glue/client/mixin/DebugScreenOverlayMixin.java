package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.events.DebugEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void glue$getGameInformation(CallbackInfoReturnable<List<String>> cir) {
        List<String> list = cir.getReturnValue();
        if (list != null) {
            DebugEvents.F3_SCREEN_LEFT.invoker().onRenderF3(Minecraft.getInstance(), list);
        }
    }

    @Inject(method = "getSystemInformation", at = @At("RETURN"))
    private void glue$getSystemInformation(CallbackInfoReturnable<List<String>> cir) {
        List<String> list = cir.getReturnValue();
        if (list != null) {
            DebugEvents.F3_SCREEN_RIGHT.invoker().onRenderF3(Minecraft.getInstance(), list);
        }
    }
}
