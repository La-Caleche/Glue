package fr.lacaleche.composite.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.lacaleche.composite.CompositeBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A menu opened on a part's block, such as a crafting table part's, stays open while its cell holds the part. */
@Mixin(AbstractContainerMenu.class)
public abstract class AbstractContainerMenuMixin {

    @ModifyReturnValue(method = "stillValid(Lnet/minecraft/world/inventory/ContainerLevelAccess;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/level/block/Block;)Z",
            at = @At("RETURN"))
    private static boolean composite$partStillValid(boolean valid, ContainerLevelAccess access, Player player, Block block) {
        if (valid) return true;
        return access.evaluate((level, pos) -> level.getBlockEntity(pos) instanceof CompositeBlockEntity cell
                && cell.holdsPartOf(block) && player.canInteractWithBlock(pos, 4.0), true);
    }
}
