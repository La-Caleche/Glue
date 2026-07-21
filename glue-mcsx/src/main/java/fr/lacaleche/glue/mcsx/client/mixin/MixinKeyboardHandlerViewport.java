package fr.lacaleche.glue.mcsx.client.mixin;

import fr.lacaleche.glue.mcsx.view.dock.McsxDockspace;
import fr.lacaleche.glue.mcsx.viewport.ViewportEmbedding;
import fr.lacaleche.glue.mcsx.viewport.ViewportInput;
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
 * Intercepts the dockspace release key while the game is captured, handing input back to the
 * dock UI instead of letting vanilla act on the key (for the default Escape, that would open
 * the pause menu — releasing to the dock is what the player meant).
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandlerViewport {

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void mcsx$onKeyPress(long handle, int key, int scanCode, int action, int mods, CallbackInfo ci) {
        if (handle != minecraft.getWindow().getWindow() || minecraft.screen != null) {
            return;
        }
        if (ViewportEmbedding.isActive() && ViewportInput.onReleaseKey(key, action)) {
            ci.cancel();
            return;
        }
        McsxDockspace dockspace = McsxDockspace.current();
        if (dockspace != null && !dockspace.isGameCaptured()
                && key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
            dockspace.onEscape();
            ci.cancel();
        }
    }
}
