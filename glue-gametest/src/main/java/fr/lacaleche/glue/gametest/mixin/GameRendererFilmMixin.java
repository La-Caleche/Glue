package fr.lacaleche.glue.gametest.mixin;

import fr.lacaleche.glue.gametest.CameraPose;
import fr.lacaleche.glue.gametest.internal.film.FilmView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The film pose's field of view replaces the option and its sprint and effect modifiers. */
@Mixin(GameRenderer.class)
public class GameRendererFilmMixin {

    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    private void glueGametest$filmFov(Camera camera, float partialTick, boolean useFovSetting,
                                      CallbackInfoReturnable<Float> cir) {
        CameraPose pose = FilmView.pose();
        if (pose != null) cir.setReturnValue(pose.fov());
    }
}
