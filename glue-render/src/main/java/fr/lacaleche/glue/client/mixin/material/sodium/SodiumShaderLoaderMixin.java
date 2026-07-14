package fr.lacaleche.glue.client.mixin.material.sodium;

import fr.lacaleche.glue.client.render.internal.material.SodiumMaterialShaderPatch;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader", remap = false)
public final class SodiumShaderLoaderMixin {

    @Inject(method = "getShaderSource", at = @At("RETURN"), cancellable = true, require = 0)
    private static void glue$patchMaterialOutputs(ResourceLocation location,
                                                   CallbackInfoReturnable<String> callback) {
        callback.setReturnValue(SodiumMaterialShaderPatch.patch(location, callback.getReturnValue()));
    }
}
