package fr.lacaleche.glue.client.mixin.material;

import com.mojang.blaze3d.shaders.ShaderType;
import fr.lacaleche.glue.client.render.internal.gbuffer.CoreShaderMaterialPatch;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Patches the fully-preprocessed source of the vanilla core shaders as it leaves the shader
 * manager, adding Lumos' material G-buffer outputs. This is the exact vanilla analogue of
 * {@code SodiumShaderLoaderMixin} -- it hooks the one method that feeds compilation, so it
 * catches both the direct pipeline-precompile path and the {@code getShader} wrapper.
 */
@Mixin(targets = "net.minecraft.client.renderer.ShaderManager$CompilationCache")
public class MixinShaderManagerCache {

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true)
    private void glue$patchMaterialOutputs(ResourceLocation location, ShaderType shaderType,
                                           CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(CoreShaderMaterialPatch.patch(location, shaderType, cir.getReturnValue()));
    }
}
