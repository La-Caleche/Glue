package fr.lacaleche.glue.web.internal.mixin;

import fr.lacaleche.glue.web.internal.host.OverlayLayers;
import fr.lacaleche.glue.web.internal.browser.RuntimeLoadingOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the global GUI submission seam: web overlays, then the runtime indicator above everything. */
@Mixin(GameRenderer.class)
abstract class GuiOverlayMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private GuiRenderState guiRenderState;

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/render/GuiRenderer;render(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;)V"))
    private void glueWeb$renderOverlays(CallbackInfo info) {
        OverlayLayers.render(this.minecraft, this.guiRenderState);
        RuntimeLoadingOverlay.render(this.minecraft, this.guiRenderState);
    }
}
