package fr.lacaleche.glue.mcsx.mui.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import fr.lacaleche.glue.mcsx.mui.UIManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws the mounted overlay above the vanilla GUI.
 * <p>
 * The GUI is retained-mode: {@code Gui}, {@code Screen} and the toasts only submit elements into
 * {@link GuiRenderState}, and nothing is drawn until {@link GuiRenderer#render} flushes and resets
 * it. Injecting at the head of that flush is therefore the last moment anything can be added to the
 * frame — inject after it and the state is already wiped.
 */
@Mixin(GuiRenderer.class)
public class MixinGuiRenderer {

    @Shadow
    @Final
    GuiRenderState renderState;

    @Inject(method = "render", at = @At("HEAD"))
    private void mcsx$renderOverlay(GpuBufferSlice fog, CallbackInfo ci) {
        if (UIManager.isInitialized()) {
            UIManager.getInstance().renderOverlay(renderState,
                    Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks());
        }
    }
}
