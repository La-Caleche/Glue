package fr.lacaleche.composite.mixin;

import net.minecraft.world.level.block.RedStoneWireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether redstone wire is computing its own power, during which wires read each other's states, not their signals. */
@Mixin(RedStoneWireBlock.class)
public interface RedStoneWireBlockAccessor {

    @Accessor("shouldSignal")
    boolean composite$shouldSignal();
}
