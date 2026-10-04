package fr.lacaleche.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.lacaleche.composite.CompositeBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A part's container, such as a chest part's, stays usable while its cell holds it. */
@Mixin(Container.class)
public interface ContainerMixin {

    @ModifyReturnValue(method = "stillValidBlockEntity(Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/player/Player;F)Z",
            at = @At("RETURN"))
    private static boolean composite$partStillValid(boolean valid, BlockEntity entity, Player player, float distance) {
        if (valid) return true;
        Level level = entity.getLevel();
        return level != null && level.getBlockEntity(entity.getBlockPos()) instanceof CompositeBlockEntity cell
                && cell.holds(entity) && player.canInteractWithBlock(entity.getBlockPos(), distance);
    }
}
