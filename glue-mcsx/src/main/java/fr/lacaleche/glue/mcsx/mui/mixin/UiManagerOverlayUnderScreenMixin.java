package fr.lacaleche.glue.mcsx.mui.mixin;

import fr.lacaleche.mui.internal.UIManager;
import fr.lacaleche.mui.internal.fabric.SimpleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keeps a mounted overlay composited underneath vanilla screens.
 *
 * <p>The host refuses to composite the overlay layer while any screen is open, because a ModernUI
 * screen renders that same layer itself. For every other screen the refusal just makes the workspace
 * vanish for as long as the screen is up — and parks the UI thread on its unconsumed frame. The
 * overlay is drawn after the HUD but before the screen, so relaxing the check to ModernUI screens
 * only leaves it visible, inert and correctly covered.</p>
 */
@Mixin(UIManager.class)
public class UiManagerOverlayUnderScreenMixin {

    @Redirect(method = "renderOverlay", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Minecraft;screen:Lnet/minecraft/client/gui/screens/Screen;"))
    private Screen mcsx$compositeUnderVanillaScreens(Minecraft minecraft) {
        Screen screen = minecraft.screen;
        return screen instanceof SimpleScreen ? screen : null;
    }
}
