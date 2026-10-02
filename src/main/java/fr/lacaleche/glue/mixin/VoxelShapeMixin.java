package fr.lacaleche.glue.mixin;

import fr.lacaleche.glue.extension.VoxelSetExtension;
import net.minecraft.world.phys.shapes.DiscreteVoxelShape;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VoxelShape.class)
public class VoxelShapeMixin implements VoxelSetExtension {

    @Shadow
    @Final
    protected DiscreteVoxelShape shape;

    @Override
    public DiscreteVoxelShape glue$getShape() {
        return shape;
    }

}
