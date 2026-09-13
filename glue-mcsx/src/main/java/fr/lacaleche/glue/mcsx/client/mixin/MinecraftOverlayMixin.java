package fr.lacaleche.glue.mcsx.client.mixin;

import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.platform.Window;

/**
 * Records the window's true framebuffer size at the top of every frame, before rendering starts.
 * A game viewport narrows the window for part of the frame; the overlay is the chrome around such a
 * viewport, so it must never see the narrowed value.
 */
@Mixin(Minecraft.class)
public class MinecraftOverlayMixin {

    @Shadow
    @Final
    private Window window;

    @Inject(method = "runTick", at = @At("HEAD"))
    private void mcsx$frameSize(boolean tick, CallbackInfo callback) {
        OverlayHost.enforceRuntimePolicy();
        OverlayHost.beginFrame(this.window.getWidth(), this.window.getHeight());
    }
}
