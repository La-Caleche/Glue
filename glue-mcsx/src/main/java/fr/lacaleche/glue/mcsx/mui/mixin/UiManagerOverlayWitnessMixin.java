package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.mui.OverlayHandle;
import fr.lacaleche.mui.internal.UIManager;
import icyllis.modernui.fragment.Fragment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the host see overlays it did not mount itself.
 *
 * <p>mui-lite holds one overlay at a time and rejects a second, and {@code MuiApi.mountOverlay} is a
 * supported entry point — so an application can take that slot without the host ever hearing about
 * it. Without this, {@code OverlayHost.isOccupied()} would report a free slot and every consumer that
 * asks before mounting (a keybind, a command) would walk straight into the rejection it was checking
 * for. The host's own mounts pass through here too, which costs nothing: the witness is keyed on the
 * returned handle, and a handle reports its own closure.</p>
 */
@Mixin(value = UIManager.class, remap = false)
public class UiManagerOverlayWitnessMixin {

    @Inject(method = "mountOverlay", at = @At("RETURN"))
    private void mcsx$witnessMount(Fragment fragment, CallbackInfoReturnable<OverlayHandle> callback) {
        OverlayHost.overlayMounted(callback.getReturnValue());
    }
}
