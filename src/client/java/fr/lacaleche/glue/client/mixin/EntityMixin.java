package fr.lacaleche.glue.client.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lacaleche.glue.block.GeometryRaycast;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Entity.class)
public class EntityMixin {

    /** The pick hits blocks where they are drawn; see {@link GeometryRaycast#clip}. */
    @WrapOperation(method = "pick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;clip(Lnet/minecraft/world/level/ClipContext;)Lnet/minecraft/world/phys/BlockHitResult;"))
    private BlockHitResult glue$pickAsDrawn(Level level, ClipContext context, Operation<BlockHitResult> original) {
        return GeometryRaycast.clip(level, context);
    }
}
