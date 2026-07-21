package fr.lacaleche.glue.client.mixin.material;

import com.mojang.blaze3d.vertex.MeshData;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells {@link GBufferCapture} which {@link RenderType} an immediate draw belongs to, for the
 * duration of that draw. The {@code setPipeline}/{@code trySetup} capture seams only see the
 * {@code RenderPipeline}, and the block-atlas particle sheet (break/run particles) shares its
 * blended pipeline with the translucent particle atlas — the render type is the only thing that
 * tells them apart. {@code draw} wraps both seams, so a HEAD/RETURN pair scopes the flag exactly.
 */
@Mixin(RenderType.CompositeRenderType.class)
public class MixinCompositeRenderTypeDraw {

    @Inject(method = "draw", at = @At("HEAD"))
    private void glue$flagDraw(MeshData meshData, CallbackInfo ci) {
        GBufferCapture.beginRenderTypeDraw((RenderType) (Object) this);
    }

    @Inject(method = "draw", at = @At("RETURN"))
    private void glue$unflagDraw(MeshData meshData, CallbackInfo ci) {
        GBufferCapture.endRenderTypeDraw();
    }
}
