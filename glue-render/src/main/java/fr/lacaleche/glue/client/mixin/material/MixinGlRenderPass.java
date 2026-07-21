package fr.lacaleche.glue.client.mixin.material;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Redirects an entity draw into Lumos' material G-buffer, keyed on the pipeline about to be used.
 *
 * <p>Every draw sets its pipeline through here -- vanilla immediate draws AND Iris's batched
 * entity flush -- so this catches world entities that never reach {@code CompositeRenderType.draw}
 * (Iris replaces that path). Entity pipelines all use the {@code NEW_ENTITY} vertex format, which
 * {@link GBufferCapture#redirectFboFor} checks. The render pass's framebuffer was bound in
 * {@code createRenderPass} and nothing rebinds it before the draw, so re-binding here (through
 * {@code GlStateManager}, keeping its cache correct) holds until {@code finishRenderPass}.
 *
 * <p>This only ARMS the redirect (records the pipeline is an entity one). The actual framebuffer
 * bind happens later at {@code trySetup} RETURN (see {@code MixinGlCommandEncoder}) -- the last
 * moment before the draw, after Iris' batched flush has done its own framebuffer management, so
 * our bind is the one that sticks. Binding here would be clobbered by Iris before the draw.
 */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlRenderPass")
public class MixinGlRenderPass {

    @Inject(method = "setPipeline(Lcom/mojang/blaze3d/pipeline/RenderPipeline;)V", at = @At("HEAD"))
    private void glue$armEntityDraw(RenderPipeline pipeline, CallbackInfo ci) {
        GBufferCapture.armForPipeline(pipeline);
    }
}
