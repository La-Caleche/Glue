package fr.lacaleche.glue.mcsx.client.mixin;

import fr.lacaleche.glue.mcsx.client.GameFocus;
import fr.lacaleche.glue.mcsx.client.internal.compat.AxiomCompatManager;
import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.glue.mcsx.client.internal.OverlayInput;
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
 * Splits the keyboard between the MCSX overlay and the game.
 *
 * <p>Ownership normally follows the cursor grab. Grabbed, vanilla reads every binding exactly as it
 * does without the workspace — that is how chat, inventory and the rest open out of gameplay — with
 * one exception: Escape returns to the workspace instead of pausing, because the workspace is the
 * thing to come back to. A screenless game overlay may temporarily release the cursor without ending
 * logical game focus; its keys continue to reach vanilla. Ungrabbed with a screen open, the screen
 * owns the keyboard outright. Ungrabbed and idle, ModernUI sees each key first; keys it leaves
 * unhandled are replayed through vanilla's complete keyboard path.</p>
 *
 * <p>Workspace Escape is layered like an editor: a focused text field gives up focus, then the
 * workspace may cancel a drag or restore a maximized pane. Idle Escape reaches vanilla and opens the
 * pause screen; it never dismisses the workspace.</p>
 */
@Mixin(KeyboardHandler.class)
public class KeyboardHandlerOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mcsx$keyPress(long window, int key, int scanCode, int action, int modifiers,
                               CallbackInfo callback) {
        if (OverlayInput.isForwardingKey()) return;
        if (window != this.minecraft.getWindow().getWindow() || !OverlayHost.isMounted()) return;

        boolean pressed = action != GLFW.GLFW_RELEASE;
        if (!OverlayHost.capturesKeyboard()
                && action == GLFW.GLFW_RELEASE
                && OverlayInput.releaseOutsideCapture(key, scanCode, modifiers)) {
            callback.cancel();
            return;
        }
        if (this.minecraft.mouseHandler.isMouseGrabbed()) {
            if (pressed && key == GLFW.GLFW_KEY_ESCAPE) {
                GameFocus.release();
                callback.cancel();
            }
            return;
        }
        if (!OverlayHost.capturesKeyboard()) return;

        // Axiom resolves this rebindable key at end-of-tick, so both press and release must reach it.
        if (AxiomCompatManager.matchesEditorUiToggle(key, scanCode)) return;

        if (action == GLFW.GLFW_PRESS && key == GLFW.GLFW_KEY_ESCAPE) {
            if (OverlayHost.escape(scanCode, modifiers)) callback.cancel();
            return;
        }
        // Vanilla resolves these ahead of screens and keybinds alike; they behave with the workspace
        // exactly as they do with a screen open.
        if (this.minecraft.options.keyFullscreen.matches(key, scanCode)
                || this.minecraft.options.keyScreenshot.matches(key, scanCode)) {
            return;
        }

        if (OverlayInput.key(key, scanCode, action, modifiers)) callback.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void mcsx$charTyped(long window, int codePoint, int modifiers, CallbackInfo callback) {
        if (window != this.minecraft.getWindow().getWindow() || !OverlayHost.capturesKeyboard()) return;

        OverlayInput.charTyped(codePoint);
        callback.cancel();
    }
}
