package fr.lacaleche.glue.client.extension;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

public interface EntityRaycastExtension {

    BlockHitResult glue$bigOutlineRaycast(double maxDistance, float tickDelta, boolean includeFluids);

    BlockHitResult glue$vanillaRaycast(double maxDistance, float tickDelta, boolean includeFluids);

    BlockHitResult glue$performRaycast(Vec3 origin, Vec3 target, double maxRange, List<Tuple<BlockPos, VoxelShape>> shapes);
}
