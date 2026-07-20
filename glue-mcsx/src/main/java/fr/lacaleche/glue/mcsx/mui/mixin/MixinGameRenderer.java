package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.mui.UIManager;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Drives the bridge's per-frame render tick.
 * <p>
 * Upstream ModernUI-MC fired custom {@code START_RENDER_TICK}/{@code END_RENDER_TICK}
 * events; the stripped bridge has none, so we call {@link UIManager#onRenderTick(boolean)}
 * directly at the head and return of {@link GameRenderer#render}.
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {

    @Inject(method = "render", at = @At("HEAD"))
    private void mcsx$onRenderTickStart(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (UIManager.isInitialized()) {
            UIManager.getInstance().onRenderTick(false);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void mcsx$onRenderTickEnd(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
        if (UIManager.isInitialized()) {
            UIManager.getInstance().onRenderTick(true);
        }
    }
}
