package fr.lacaleche.glue.client.mixin.material.sodium;

import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialHooks;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ShaderChunkRenderer.class, remap = false)
public abstract class SodiumShaderChunkRendererMixin {

    @Inject(method = "begin", at = @At("RETURN"), require = 0)
    private void glue$beginMaterialPass(TerrainRenderPass renderPass, FogParameters fog,
                                        CallbackInfo callback) {
        TerrainMaterialHooks.beginSodiumPass(renderPass);
    }

    @Inject(method = "end", at = @At("HEAD"), require = 0)
    private void glue$endMaterialPass(TerrainRenderPass renderPass, CallbackInfo callback) {
        TerrainMaterialHooks.endSodiumPass();
    }
}
