package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.block.GeometryRaycast;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One trace of the camera's pick, as the raycast overlay and the Raycast page show it: vanilla's hit
 * next to the cell hit and the final one from {@link GeometryRaycast#trace}.
 */
public record RaycastProbe(Level level, ClipContext context, BlockHitResult vanilla, GeometryRaycast.Trace trace) {

    public static final double MAX_DISTANCE = 20.0;

    /** Traces from the camera entity's eyes along its view, or null without a world or a camera. */
    public static @Nullable RaycastProbe probe() {
        Minecraft client = Minecraft.getInstance();
        Entity entity = client.cameraEntity;
        if (client.level == null || client.player == null || entity == null) return null;

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaTicks();
        Vec3 origin = entity.getEyePosition(tickDelta);
        Vec3 target = origin.add(entity.getViewVector(tickDelta).scale(MAX_DISTANCE));
        ClipContext context = new ClipContext(origin, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                entity);
        Level level = entity.level();
        return new RaycastProbe(level, context, level.clip(context), GeometryRaycast.trace(level, context));
    }

    public Vec3 origin() {
        return this.context.getFrom();
    }

    public Vec3 target() {
        return this.context.getTo();
    }

    /** Whether the final hit picks the block and face vanilla's does, or both miss. */
    public boolean matchesVanilla() {
        BlockHitResult result = this.trace.result();
        if (this.vanilla.getType() == HitResult.Type.MISS || result.getType() == HitResult.Type.MISS) {
            return this.vanilla.getType() == result.getType();
        }

        return this.vanilla.getBlockPos().equals(result.getBlockPos())
                && this.vanilla.getDirection() == result.getDirection();
    }
}
