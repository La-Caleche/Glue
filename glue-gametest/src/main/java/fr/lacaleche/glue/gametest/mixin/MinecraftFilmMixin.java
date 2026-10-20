package fr.lacaleche.glue.gametest.mixin;

import fr.lacaleche.glue.gametest.internal.film.FilmView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Code that reads the frame time from the client sees the film's fixed step. */
@Mixin(Minecraft.class)
public class MinecraftFilmMixin {

    @Inject(method = "getDeltaTracker", at = @At("HEAD"), cancellable = true)
    private void glueGametest$filmDelta(CallbackInfoReturnable<DeltaTracker> cir) {
        DeltaTracker delta = FilmView.delta();
        if (delta != null) cir.setReturnValue(delta);
    }
}
