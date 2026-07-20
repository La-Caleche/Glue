package fr.lacaleche.glue.mcsx.client.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.textures.GpuTextureView;
import fr.lacaleche.glue.mcsx.viewport.DockPresent;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects the final present while the game is embedded: instead of stretching the (pane-sized)
 * main target over the whole window, the frame is blitted into the viewport pane's sub-rectangle
 * and the rest of the window is left to the dock UI drawn afterwards.
 */
@Mixin(GlCommandEncoder.class)
public class MixinGlCommandEncoder {

    @Shadow
    @Final
    private GlDevice device;

    @Shadow
    @Final
    private int drawFbo;

    @Inject(method = "presentTexture",
            at = @At(value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_disableScissorTest()V"),
            cancellable = true)
    private void mcsx$presentIntoPane(GpuTextureView texture, CallbackInfo ci) {
        if (ViewportEmbedding.shouldRedirectPresent()) {
            DockPresent.blitGameToPane(texture, device, drawFbo);
            ci.cancel();
        }
    }
}
