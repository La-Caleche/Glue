package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.glue.mcsx.mui.UIManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the keyboard to an interactive overlay.
 * <p>
 * A screen gets its keys from {@code Screen#keyPressed}; an overlay has no such callback, and with
 * {@code Minecraft.screen == null} vanilla would keep firing keybinds — typing a coordinate into a
 * field would make the player walk. So while the overlay owns the keyboard (see
 * {@code UIManager#overlayOwnsKeyboard}: outright with no vanilla screen, pointer/last-click-based
 * with one open) we forward the event to the view tree and cancel vanilla's handling outright.
 * <p>
 * Escape is deliberately left alone: what it means (close the HUD, hand the cursor back, open the
 * pause menu) is the host's policy, not Modern UI's.
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mcsx$onKeyPress(long handle, int key, int scanCode, int action, int mods, CallbackInfo ci) {
        if (handle != minecraft.getWindow().getWindow() || !UIManager.isInitialized()
                || key == GLFW.GLFW_KEY_ESCAPE) {
            return;
        }
        UIManager manager = UIManager.getInstance();
        if (!manager.overlayOwnsKeyboard()) {
            return;
        }
        if (action == GLFW.GLFW_RELEASE) {
            manager.onKeyRelease(key, scanCode, mods);
        } else {
            manager.onKeyPress(key, scanCode, mods);
        }
        ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void mcsx$onCharTyped(long handle, int codePoint, int mods, CallbackInfo ci) {
        if (handle != minecraft.getWindow().getWindow() || !UIManager.isInitialized()) {
            return;
        }
        UIManager manager = UIManager.getInstance();
        if (!manager.overlayOwnsKeyboard()) {
            return;
        }
        for (char ch : Character.toChars(codePoint)) {
            manager.onCharTyped(ch);
        }
        ci.cancel();
    }
}
