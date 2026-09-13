package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.client.internal.OverlayInput;
import icyllis.modernui.view.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reports keys that reached mui-lite's terminal unhandled-key seam. */
@Mixin(targets = "fr.lacaleche.mui.internal.UIManager$HostViewRoot", remap = false)
public class HostViewRootKeyMixin {

    @Inject(method = "onKeyEvent", at = @At("HEAD"))
    private void mcsx$unhandledKey(KeyEvent event, CallbackInfo callback) {
        OverlayInput.unhandledKey(event);
    }
}
