package fr.lacaleche.glue.mcsx.mui.mixin;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.mui.internal.UIManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sizes the composited ModernUI layer against the window the overlay actually occupies.
 *
 * <p>The host blits its layer at the live {@code Window} size. A game viewport reports the viewport's
 * dimensions from {@code Window} for part of the frame, and the overlay is composited inside that
 * window — so without this the workspace would be squeezed into the viewport rectangle it is supposed
 * to surround. The frame's true size is captured before rendering starts, which makes the result
 * independent of the order the two mixins happen to run in.</p>
 */
@Mixin(UIManager.class)
public class UiManagerCompositeMixin {

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int mcsx$layerWidth(Window window) {
        int width = OverlayHost.frameWidth();
        return width > 0 ? width : window.getWidth();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int mcsx$layerHeight(Window window) {
        int height = OverlayHost.frameHeight();
        return height > 0 ? height : window.getHeight();
    }
}
