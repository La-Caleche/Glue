package fr.lacaleche.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositeBlocks;
import fr.lacaleche.composite.PartScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets a {@link PartScope} stand a composite part at its cell's position, and parts receive their block events. */
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

    /** A block event the client receives for a part's block reaches the part, as the server's did. */
    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true)
    private void composite$partEvent(BlockPos pos, Block block, int paramA, int paramB, CallbackInfo ci) {
        Level level = (Level) (Object) this;
        if (block == CompositeBlocks.COMPOSITE || !level.getBlockState(pos).is(CompositeBlocks.COMPOSITE)) return;
        if (!(level.getBlockEntity(pos) instanceof CompositeBlockEntity cell)) return;
        cell.runPartOf(block, scope -> scope.state().triggerEvent(level, pos, paramA, paramB));
        ci.cancel();
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
