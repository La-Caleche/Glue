package fr.lacaleche.glue.mcsx.client.mixin;

import fr.lacaleche.glue.mcsx.client.internal.OverlayHost;
import fr.lacaleche.glue.mcsx.client.internal.OverlayInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Splits the pointer between the MCSX overlay and the game.
 *
 * <p>Ownership normally follows the cursor grab: a grabbed pointer plays the game untouched, an
 * ungrabbed one belongs to the workspace. Logical game focus remains authoritative while a
 * screenless game overlay temporarily releases the cursor. With a vanilla screen open, the split is
 * positional — the screen keeps events inside the game surface it is laid out in, the workspace
 * keeps everything outside — and a button release always goes wherever its press went, so neither
 * side is left holding a phantom drag.</p>
 *
 * <p>{@code grabMouse} is refused while the workspace is mounted unless it deliberately handed focus
 * to the game: vanilla re-locks the cursor whenever a screen closes, and the pointer must come back
 * to the workspace instead — or back to mouselook when the screen was opened from it.</p>
 */
@Mixin(MouseHandler.class)
public class MouseHandlerOverlayMixin {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "onMove", at = @At("TAIL"))
    private void mcsx$hover(long window, double x, double y, CallbackInfo callback) {
        if (window == this.minecraft.getWindow().getWindow() && OverlayHost.capturesPointer()) {
            OverlayInput.hoverMove();
        }
    }

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void mcsx$press(long window, int button, int action, int modifiers, CallbackInfo callback) {
        if (window != this.minecraft.getWindow().getWindow()) return;

        boolean captured = action == GLFW.GLFW_PRESS
                ? OverlayHost.capturesPointer()
                : OverlayInput.isHeld(button);
        if (!captured) return;

        OverlayInput.mouseButton(button, action, modifiers);
        callback.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void mcsx$scroll(long window, double horizontal, double vertical, CallbackInfo callback) {
        if (window != this.minecraft.getWindow().getWindow() || !OverlayHost.capturesPointer()) return;

        OverlayInput.scroll(horizontal, vertical);
        callback.cancel();
    }

    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mcsx$keepCursorFree(CallbackInfo callback) {
        if (OverlayHost.isMounted() && !OverlayHost.isGameFocused()) callback.cancel();
    }
}
