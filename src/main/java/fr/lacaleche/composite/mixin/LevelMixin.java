package fr.lacaleche.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositeBlocks;
import fr.lacaleche.composite.PartScope;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

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

    /**
     * Places a scoped part's state instead of the chunk's block; the rest of {@code setBlock} then
     * updates neighbours, shapes and comparators as for a plain block, reading the part's state back.
     */
    @WrapOperation(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/chunk/LevelChunk;setBlockState(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/world/level/block/state/BlockState;"))
    private BlockState composite$placePartState(LevelChunk chunk, BlockPos pos, BlockState state, int flags,
                                                Operation<BlockState> original) {
        PartScope scope = PartScope.at((Level) (Object) this, pos);
        return scope == null ? original.call(chunk, pos, state, flags) : scope.place(state, flags);
    }
}
