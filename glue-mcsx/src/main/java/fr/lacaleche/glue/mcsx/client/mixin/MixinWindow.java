package fr.lacaleche.glue.mcsx.client.mixin;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps the framebuffer pinned to the viewport pane across OS resize events. The embedding
 * resizes the window itself when pane bounds change; this hook covers the other direction — the
 * user resizing the real window would otherwise reset the framebuffer to the full window size.
 */
@Mixin(Window.class)
public class MixinWindow {

    @Shadow
    private int framebufferWidth;

    @Shadow
    private int framebufferHeight;

    @Inject(method = "onFramebufferResize",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I", ordinal = 1))
    private void mcsx$pinFramebufferToPane(long window, int width, int height, CallbackInfo ci) {
        // Pin to the size applied at the frame boundary, not a newer UI-thread publication.
        if (ViewportEmbedding.pinnedWidth() > 0) {
            framebufferWidth = ViewportEmbedding.pinnedWidth();
            framebufferHeight = ViewportEmbedding.pinnedHeight();
        }
    }
}
