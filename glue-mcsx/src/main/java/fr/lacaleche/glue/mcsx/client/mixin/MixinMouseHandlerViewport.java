package fr.lacaleche.glue.mcsx.client.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.mcsx.viewport.ViewportBounds;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The game-side half of embedded-viewport input. While the framebuffer is pinned, cursor
 * positions are rewritten from real-window space into the virtual viewport before vanilla sees
 * them (the raw position is stashed first for the dock UI); cursor grab/release follows the
 * dockspace's input state instead of vanilla's, and a released cursor re-centers into the pane.
 */
@Mixin(MouseHandler.class)
public class MixinMouseHandlerViewport {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private boolean mouseGrabbed;

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @ModifyVariable(method = "onMove", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private double mcsx$remapX(double x, long window, double rawX, double rawY) {
        if (window != minecraft.getWindow().getWindow() || !ViewportEmbedding.isActive()) {
            return x;
        }
        ViewportEmbedding.setRawCursor(rawX, rawY);
        return ViewportEmbedding.remapX(x);
    }

    @ModifyVariable(method = "onMove", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double mcsx$remapY(double y, long window) {
        if (window != minecraft.getWindow().getWindow() || !ViewportEmbedding.isActive()) {
            return y;
        }
        return ViewportEmbedding.remapY(y);
    }

    /** The dock UI owns the pointer: mouse deltas must not turn the player. */
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void mcsx$blockLook(CallbackInfo ci) {
        if (ViewportEmbedding.isActive() && ViewportInput.dockOwnsPointer()) {
            ci.cancel();
        }
    }

    /** Vanilla (screen close, world click) must not re-grab while the dock owns the cursor. */
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void mcsx$blockGrab(CallbackInfo ci) {
        if (ViewportInput.isForceUnlock()) {
            ci.cancel();
        }
    }

    /**
     * A cursor released while embedded appears at the centre of the viewport pane, not the centre
     * of the real window — vanilla's centre would drop it somewhere in the dock chrome.
     *
     * <p>The pane centre is a <em>real-window</em> position. {@code xpos/ypos} live in remapped
     * virtual-pane space while embedded, and {@code glfwSetCursorPos} fires no move callback, so
     * both the vanilla fields and the dock's raw-cursor stash must be written by hand here — the
     * stash otherwise still holds unbounded disabled-cursor deltas from the capture, and the
     * first post-release click would hit-test against those and swing in the world.
     */
    @Inject(method = "releaseMouse", at = @At("HEAD"), cancellable = true)
    private void mcsx$releaseIntoPane(CallbackInfo ci) {
        ViewportBounds bounds = ViewportEmbedding.bounds();
        if (!mouseGrabbed || bounds == null) {
            return;
        }
        mouseGrabbed = false;
        double realX = bounds.x() + bounds.width() / 2.0;
        double realY = bounds.top() + bounds.height() / 2.0;
        xpos = ViewportEmbedding.remapX(realX);
        ypos = ViewportEmbedding.remapY(realY);
        ViewportEmbedding.setRawCursor(realX, realY);
        InputConstants.grabOrReleaseMouse(minecraft.getWindow().getWindow(),
                InputConstants.CURSOR_NORMAL, realX, realY);
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), realX, realY);
        ci.cancel();
    }

    /** Lets a HOLD-mode capture end on the button-up that matches it. */
    @Inject(method = "onPress", at = @At("HEAD"))
    private void mcsx$onRawButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (window == minecraft.getWindow().getWindow() && ViewportEmbedding.isActive()) {
            ViewportInput.onRawMouseButton(button, action);
        }
    }
}
