package fr.lacaleche.glue.client.mixin.shader.runtime;

import com.mojang.blaze3d.opengl.GlDevice;
import fr.lacaleche.glue.client.shader.internal.RuntimePipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GlDevice.class)
public class GlDeviceMixin {

    /** Runtime pipelines have no resource source, so they compile again before any frame meets the empty cache. */
    @Inject(method = "clearPipelineCache", at = @At("TAIL"))
    private void glue$recompileRuntimePipelines(CallbackInfo ci) {
        RuntimePipelines.recompileAll();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void glue$stopRecompiling(CallbackInfo ci) {
        RuntimePipelines.shutdown();
    }
}
