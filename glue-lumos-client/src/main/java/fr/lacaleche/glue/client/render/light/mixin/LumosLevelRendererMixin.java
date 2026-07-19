package fr.lacaleche.glue.client.render.light.mixin;

import fr.lacaleche.glue.client.render.light.LightRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LumosLevelRendererMixin {

    @Inject(method = "blockChanged", at = @At("HEAD"))
    private void invalidateLightShadows(BlockGetter level, BlockPos pos, BlockState oldState,
                                        BlockState newState, int flags, CallbackInfo ci) {
        LightRenderer.onBlockChanged(pos);
    }
}
