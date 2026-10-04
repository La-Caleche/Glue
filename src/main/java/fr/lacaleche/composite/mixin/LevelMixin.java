package fr.lacaleche.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.lacaleche.composite.PartScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets a {@link PartScope} stand a composite part at its cell's position. */
@Mixin(Level.class)
public abstract class LevelMixin {

    @ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
    private BlockState composite$partState(BlockState state, BlockPos pos) {
        PartScope scope = PartScope.at((Level) (Object) this, pos);
        return scope == null ? state : scope.state();
    }

    @ModifyReturnValue(method = "getBlockEntity", at = @At("RETURN"))
    private BlockEntity composite$partEntity(BlockEntity entity, BlockPos pos) {
        PartScope scope = PartScope.at((Level) (Object) this, pos);
        return scope == null ? entity : scope.entity();
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), cancellable = true)
    private void composite$setPartState(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                        CallbackInfoReturnable<Boolean> cir) {
        PartScope scope = PartScope.at((Level) (Object) this, pos);
        if (scope == null) return;
        scope.setState(state);
        cir.setReturnValue(true);
    }
}
