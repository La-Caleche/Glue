package fr.lacaleche.glue.client.mixin.viewport;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.viewport.internal.GameViewportStage;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lays screens out against the game viewport while it is active.
 *
 * <p>A screen learns its size exactly twice — {@code init} on open and {@code resize} on a window
 * resize — and both read the window's GUI-scaled dimensions at these call sites. Reporting the
 * viewport's dimensions instead is what makes chat, inventory and menus open inside the viewport
 * pane rather than across the whole workspace; the render transform and the pointer remap carry the
 * same space through drawing and input.</p>
 */
@Mixin(Minecraft.class)
public class MinecraftScreenViewportMixin {

    @Redirect(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int glue$screenInitWidth(Window window) {
        return GameViewportStage.effectiveGuiWidth(window);
    }

    @Redirect(method = "setScreen", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int glue$screenInitHeight(Window window) {
        return GameViewportStage.effectiveGuiHeight(window);
    }

    @Redirect(method = "resizeDisplay", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int glue$screenResizeWidth(Window window) {
        return GameViewportStage.effectiveGuiWidth(window);
    }

    @Redirect(method = "resizeDisplay", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int glue$screenResizeHeight(Window window) {
        return GameViewportStage.effectiveGuiHeight(window);
    }
}
