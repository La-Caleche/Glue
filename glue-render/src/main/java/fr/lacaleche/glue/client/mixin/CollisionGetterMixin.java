package fr.lacaleche.glue.client.mixin;

import com.google.common.collect.ImmutableList;
import fr.lacaleche.glue.block.IHaveBigOutline;
import fr.lacaleche.glue.client.extension.CollisionViewExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockCollisions;
import net.minecraft.world.level.CollisionGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(CollisionGetter.class)
public interface CollisionGetterMixin extends CollisionViewExtension {

    @Override
    default Iterable<Tuple<BlockPos, VoxelShape>> glue$getBlockCollisions(@Nullable Entity entity, AABB box) {
        if (entity == null) return ImmutableList.of();
        return () -> new BlockCollisions<>(entity.level(), entity, box, false, (pos, voxelShape) -> {
            final BlockState state = entity.level().getBlockState(pos);
            if (!(state.getBlock() instanceof IHaveBigOutline))
                return new Tuple<>(null, Shapes.empty());
            return new Tuple<>(pos.immutable(), state.getCollisionShape(entity.level(), pos, CollisionContext.empty()));
        });
    }
}
