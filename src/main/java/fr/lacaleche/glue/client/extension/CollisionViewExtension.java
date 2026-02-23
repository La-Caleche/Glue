package fr.lacaleche.glue.client.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public interface CollisionViewExtension {

    Iterable<Tuple<BlockPos, VoxelShape>> glue$getBlockCollisions(@Nullable Entity entity, AABB box);

}
