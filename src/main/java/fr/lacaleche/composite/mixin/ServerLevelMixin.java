package fr.lacaleche.composite.mixin;

import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositeBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockEventData;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hands a scheduled tick or a block event addressed to a part's block, at its cell's position, to
 * that part: a chest part rechecks its viewers and opens its lid.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "tickBlock", at = @At("HEAD"), cancellable = true)
    private void composite$tickPart(BlockPos pos, Block block, CallbackInfo ci) {
        CompositeBlockEntity cell = composite$cell(pos, block);
        if (cell == null) return;
        ServerLevel level = (ServerLevel) (Object) this;
        cell.runPartOf(block, scope -> {
            scope.state().tick(level, pos, level.getRandom());
            return true;
        });
        ci.cancel();
    }

    @Inject(method = "doBlockEvent", at = @At("HEAD"), cancellable = true)
    private void composite$partEvent(BlockEventData event, CallbackInfoReturnable<Boolean> cir) {
        CompositeBlockEntity cell = composite$cell(event.pos(), event.block());
        if (cell == null) return;
        ServerLevel level = (ServerLevel) (Object) this;
        cir.setReturnValue(cell.runPartOf(event.block(),
                scope -> scope.state().triggerEvent(level, event.pos(), event.paramA(), event.paramB())).orElse(false));
    }

    /** The cell at a position when something is addressed there to another block than the cell's. */
    private @Nullable CompositeBlockEntity composite$cell(BlockPos pos, Block block) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (block == CompositeBlocks.COMPOSITE || !level.getBlockState(pos).is(CompositeBlocks.COMPOSITE)) return null;
        return level.getBlockEntity(pos) instanceof CompositeBlockEntity cell ? cell : null;
    }
}
