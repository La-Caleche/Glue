package fr.lacaleche.composite.client.mixin;

import fr.lacaleche.composite.PartScope;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sets a scoped part's state before the client records it as a predicted block, which would later
 * restore the part's state over the cell.
 */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMixin {

    @Inject(method = "setBlock", at = @At("HEAD"), cancellable = true)
    private void composite$setPartState(BlockPos pos, BlockState state, int flags, int recursionLeft,
                                        CallbackInfoReturnable<Boolean> cir) {
        PartScope scope = PartScope.at((ClientLevel) (Object) this, pos);
        if (scope == null) return;
        scope.setState(state);
        cir.setReturnValue(true);
    }
}
