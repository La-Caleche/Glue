package fr.lacaleche.glue.client.mixin;

import fr.lacaleche.glue.client.events.DebugEvents;
import fr.lacaleche.glue.client.events.ParticleManagerEvents;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Environment(EnvType.CLIENT)
@Mixin(ParticleEngine.class)
public class ParticleEngineMixin {

    @Shadow
    protected ClientLevel level;

    @Redirect(method = "destroy", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;getShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/shapes/VoxelShape;"))
    private VoxelShape glue$addBlockBreakParticles(BlockState instance, BlockGetter blockView, BlockPos blockPos) {
        return ParticleManagerEvents.BLOCK_BREAK.invoker().onGetBreakParticlesShape(instance, this.level, blockView,
                blockPos);
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void glue$onParticleCreate(ParticleOptions parameters, double x, double y,
                                       double z, double velocityX, double velocityY, double velocityZ,
                                       CallbackInfoReturnable<Particle> cir) {
        Particle particle = cir.getReturnValue();
        if (particle != null) {
            DebugEvents.PARTICLE_SPAWN.invoker().onParticleSpawn(particle, this.level,
                    x, y, z, velocityX, velocityY, velocityZ);
        }
    }

}
