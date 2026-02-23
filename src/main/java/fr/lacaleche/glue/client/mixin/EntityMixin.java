package fr.lacaleche.glue.client.mixin;

import com.google.common.collect.ImmutableList;
import fr.lacaleche.glue.client.extension.CollisionViewExtension;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;

import fr.lacaleche.glue.client.extension.EntityRaycastExtension;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.Objects;

import static net.minecraft.world.phys.HitResult.Type.MISS;

@Mixin(Entity.class)
public class EntityMixin implements EntityRaycastExtension {

    @Inject(method = "pick", at = @At("RETURN"), cancellable = true)
    private void glue$pick(double maxDistance, float tickDelta, boolean includeFluids, CallbackInfoReturnable<HitResult> cir) {
        final BlockHitResult vanillaResult = (BlockHitResult) cir.getReturnValue();
        final BlockHitResult bigOutlineResult = glue$bigOutlineRaycast(maxDistance, tickDelta, includeFluids);
        if (bigOutlineResult == null || bigOutlineResult.getType() == MISS) return;
        if (vanillaResult == null || vanillaResult.getType() == MISS) {
            cir.setReturnValue(bigOutlineResult);
            return;
        }

        final Entity self = (Entity) (Object) this;

        final BlockState vanillaState = self.level().getBlockState(vanillaResult.getBlockPos());
        final BlockState bigOutlineState = self.level().getBlockState(bigOutlineResult.getBlockPos());
        if (vanillaState.isAir() || vanillaState == bigOutlineState) {
            cir.setReturnValue(bigOutlineResult);
            return;
        }

        final Vec3 origin = self.getEyePosition(tickDelta);
        final double vanillaDistance = vanillaResult.getLocation().distanceToSqr(origin);
        final double bigOutlineDistance = bigOutlineResult.getLocation().distanceToSqr(origin);

        if (bigOutlineDistance < vanillaDistance) {
            cir.setReturnValue(bigOutlineResult);
        }
    }

    @Override
    public BlockHitResult glue$vanillaRaycast(double maxDistance, float tickDelta, boolean includeFluids) {
        final Entity self = (Entity) (Object) this;
        Vec3 vec3d = self.getEyePosition(tickDelta);
        Vec3 vec3d2 = self.getViewVector(tickDelta);
        Vec3 vec3d3 = vec3d.add(vec3d2.x * maxDistance, vec3d2.y * maxDistance, vec3d2.z * maxDistance);
        return self.level().clip(new ClipContext(vec3d, vec3d3, ClipContext.Block.OUTLINE, includeFluids ? ClipContext.Fluid.ANY : ClipContext.Fluid.NONE, self));
    }

    @Override
    public BlockHitResult glue$bigOutlineRaycast(double maxDistance, float tickDelta, boolean includeFluids) {
        final Entity self = (Entity) (Object) this;
        final CollisionViewExtension world = (CollisionViewExtension) self.level();

        final Vec3 origin = self.getEyePosition(tickDelta);
        final Vec3 rotation = self.getViewVector(tickDelta);
        final Vec3 target = origin.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);

        return this.glue$performRaycast(origin, target, maxDistance, ImmutableList.copyOf(world.glue$getBlockCollisions(self, self.getBoundingBox().inflate(6.0))).stream().filter(pair -> pair.getA() != null).toList());
    }

    @Override
    public BlockHitResult glue$performRaycast(Vec3 origin, Vec3 target, double maxRange, List<Tuple<BlockPos, VoxelShape>> shapes) {
        BlockHitResult closestHit = null;
        double maxRangeSquared = maxRange * maxRange;

        for (Tuple<BlockPos, VoxelShape> shape : shapes) {
            final BlockHitResult hit = shape.getB().clip(origin, target, shape.getA());
            if (hit == null) continue;

            final Vec3 hitPos = hit.getLocation();
            final double distance = hitPos.distanceToSqr(origin);

            double hitDistanceSquared = hit.getLocation().distanceToSqr(origin);
            if (hitDistanceSquared >= maxRangeSquared)
                continue;

            if (closestHit != null && hitDistanceSquared >= closestHit.getLocation().distanceToSqr(origin))
                continue;

            if (closestHit == null || distance < Objects.requireNonNull(closestHit).getLocation().distanceToSqr(origin)) {
                closestHit = hit;
            }
        }

        return closestHit;
    }

}
