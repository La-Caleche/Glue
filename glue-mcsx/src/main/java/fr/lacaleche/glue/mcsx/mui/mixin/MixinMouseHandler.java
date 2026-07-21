package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.mui.UIManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Routes raw mouse-button input into the bridge.
 * <p>
 * Vendored from ModernUI-MC ({@code icyllis.modernui.mc.mixin.MixinMouseHandler})
 * and relocated; the commented-out scroll/local-capture variants were dropped. The
 * overlay intercept is ours.
 */
@Mixin(MouseHandler.class)
public class MixinMouseHandler {

    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * Hands the click to an interactive overlay before vanilla sees it. This has to run at HEAD and
     * cancel: with no screen open, vanilla treats any press as "the player clicked the world", which
     * would both re-grab the cursor and swing the arm — behind the HUD the player just clicked.
     */
    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void mcsx$onMouseButtonPre(long handle, int button, int action, int mods, CallbackInfo ci) {
        if (handle == minecraft.getWindow().getWindow() && UIManager.isInitialized()
                && UIManager.getInstance().interceptMouseButton(button, action, mods)) {
            ci.cancel();
        }
    }

    @Inject(method = "onPress", at = @At("TAIL"))
    private void mcsx$onMouseButtonPost(long handle, int button, int action, int mods, CallbackInfo ci) {
        if (handle == minecraft.getWindow().getWindow() && UIManager.isInitialized()) {
            UIManager.getInstance().onPostMouseInput(button, action, mods);
        }
    }

    /**
     * Feeds hover to an interactive overlay. With a screen open the screen's {@code mouseMoved}
     * does this; with only an overlay mounted there is no such callback, so we take it from the
     * raw cursor-position event.
     */
    @Inject(method = "onMove", at = @At("TAIL"))
    private void mcsx$onMove(long handle, double x, double y, CallbackInfo ci) {
        if (handle == minecraft.getWindow().getWindow() && UIManager.isInitialized()
                && UIManager.getInstance().isOverlayPointerAvailable()) {
            UIManager.getInstance().onHoverMove(true);
        }
    }

    /** Scrolls the overlay instead of the hotbar (or the in-pane screen) when the cursor is over it. */
    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mcsx$onScroll(long handle, double scrollX, double scrollY, CallbackInfo ci) {
        if (handle == minecraft.getWindow().getWindow() && UIManager.isInitialized()) {
            UIManager manager = UIManager.getInstance();
            if (manager.isOverlayPointerAvailable() && manager.isPointerOverOverlay()) {
                manager.onScroll(scrollX, scrollY);
                ci.cancel();
            }
        }
    }
}
