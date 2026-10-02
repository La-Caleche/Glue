package fr.lacaleche.glue.mixin;

import fr.lacaleche.glue.shaper.BlockShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies generated shapes. These are the base implementations, so a block whose class overrides
 * them without calling {@code super} keeps its own shape.
 */
@Mixin(BlockBehaviour.class)
public abstract class BlockBehaviourMixin {

    @Shadow
    @Final
    protected boolean hasCollision;

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void glue$generatedShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                     CallbackInfoReturnable<VoxelShape> cir) {
        VoxelShape shape = BlockShapes.outline(state);
        if (shape != null) cir.setReturnValue(shape);
    }

    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void glue$generatedCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context,
                                              CallbackInfoReturnable<VoxelShape> cir) {
        if (!this.hasCollision) return;
        VoxelShape shape = BlockShapes.collision(state);
        if (shape != null) cir.setReturnValue(shape);
    }
}
