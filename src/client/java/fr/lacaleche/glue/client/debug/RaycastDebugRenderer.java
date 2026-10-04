package fr.lacaleche.glue.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.block.GeometryRaycast;
import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.BOX;
import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.MARKER;

/**
 * Shows how the camera's pick is decided: the blocks reaching past their cells that it tested, and
 * vanilla's hit next to the cell hit and the final one from {@link GeometryRaycast#trace}.
 */
public class RaycastDebugRenderer extends GlueDebugRenderer {

    private static final double MAX_DISTANCE = 20.0;

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                       double cameraZ) {
        if (!this.enabled)
            return;
        Ray ray = ray();
        if (ray == null)
            return;

        GeometryRaycast.Trace trace = GeometryRaycast.trace(ray.level(), ray.context());
        for (BlockPos pos : trace.reaching()) {
            this.addDebugElement(pos, builder -> builder.type(BOX).color(Color.ofRGBA(200, 200, 200, 255)));
        }
        if (trace.result().getType() != HitResult.Type.MISS)
            this.addDebugElement(trace.result().getBlockPos(),
                    builder -> builder.type(MARKER).color(255, 100, 100).message("Hit result").duration(25));

        super.renderElements(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
    }

    @Override
    public void renderHud(GuiGraphics context) {
        if (!this.enabled)
            return;
        Ray ray = ray();
        if (ray == null)
            return;

        BlockHitResult vanilla = ray.level().clip(ray.context());
        GeometryRaycast.Trace trace = GeometryRaycast.trace(ray.level(), ray.context());
        Vec3 origin = ray.context().getFrom();

        List<String> texts = new ArrayList<>();
        texts.add("Raycast debug");
        texts.add("");
        texts.add("Origin: " + BlockPos.containing(origin).toShortString());
        texts.add("Target: " + BlockPos.containing(ray.context().getTo()).toShortString());
        texts.add("Max Range: " + MAX_DISTANCE);
        texts.add("");

        if (!trace.reaching().isEmpty()) {
            texts.add("Reaching past their cells:");
            for (BlockPos pos : trace.reaching()) {
                texts.add(pos.toShortString() + ": " + ray.level().getBlockState(pos));
            }
            texts.add("");
        }

        resultText(texts, "Vanilla", vanilla, ray.level(), origin);
        resultText(texts, "Cells", trace.cell(), ray.level(), origin);
        resultText(texts, "Final", trace.result(), ray.level(), origin);
        texts.add("Final matches vanilla: " + sameTarget(vanilla, trace.result()));

        this.drawRight(context, texts, Minecraft.getInstance());
    }

    private static @Nullable Ray ray() {
        Minecraft client = Minecraft.getInstance();
        Entity entity = client.cameraEntity;
        if (client.level == null || client.player == null || entity == null)
            return null;

        float tickDelta = client.getDeltaTracker().getGameTimeDeltaTicks();
        Vec3 origin = entity.getEyePosition(tickDelta);
        Vec3 target = origin.add(entity.getViewVector(tickDelta).scale(MAX_DISTANCE));
        return new Ray(entity.level(), new ClipContext(origin, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, entity));
    }

    private static boolean sameTarget(BlockHitResult first, BlockHitResult second) {
        if (first.getType() == HitResult.Type.MISS || second.getType() == HitResult.Type.MISS)
            return first.getType() == second.getType();
        return first.getBlockPos().equals(second.getBlockPos()) && first.getDirection() == second.getDirection();
    }

    private static void resultText(List<String> texts, String name, @Nullable BlockHitResult result, Level level, Vec3 origin) {
        if (result == null || result.getType() == HitResult.Type.MISS) {
            texts.add(name + ": MISS");
        } else {
            texts.add(name + " Pos: " + result.getBlockPos().toShortString() + " " + result.getDirection());
            texts.add(name + " State: " + level.getBlockState(result.getBlockPos()));
            texts.add(name + " Distance: " + String.format("%.2f", result.getLocation().distanceTo(origin)));
        }
        texts.add("");
    }

    private record Ray(Level level, ClipContext context) {
    }
}
