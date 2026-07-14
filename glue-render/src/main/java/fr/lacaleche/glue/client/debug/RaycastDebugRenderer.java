package fr.lacaleche.glue.client.debug;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.extension.CollisionViewExtension;
import fr.lacaleche.glue.client.extension.EntityRaycastExtension;
import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.BOX;
import static fr.lacaleche.glue.client.debug.GlueDebugRenderer.ElementType.MARKER;

public class RaycastDebugRenderer extends GlueDebugRenderer {

    public RaycastDebugRenderer() {
    }

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                       double cameraZ) {
        if (!this.enabled)
            return;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null || client.cameraEntity == null)
            return;

        final Entity entity = client.cameraEntity;
        final CollisionViewExtension world = (CollisionViewExtension) entity.level();
        final AtomicInteger hitCount = new AtomicInteger(0);

        final double maxDistance = 20.0;
        final Vec3 origin = entity.getEyePosition(0.0f);
        final Vec3 rotation = entity.getViewVector(0.0f);
        final Vec3 target = origin.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);

        final HitResult pick = entity.pick(maxDistance, 0.0F, false);
        final BlockHitResult result = pick instanceof BlockHitResult br ? br : null;
        final List<Tuple<BlockPos, VoxelShape>> bigOutlineShapes = ImmutableList
                .copyOf(world.glue$getBlockCollisions(entity, entity.getBoundingBox().inflate(6.0))).stream()
                .filter(pair -> pair.getA() != null).toList();

        bigOutlineShapes.forEach(pair -> {
            final BlockPos blockPos = pair.getA();
            this.addDebugElement(blockPos.immutable(),
                    builder -> builder.type(BOX).color(Color.ofRGBA(200, 200, 200, 255)));

            final BlockHitResult hit = pair.getB().clip(origin, target, blockPos);
            if (hit == null)
                return;

            this.addDebugElement(hit.getBlockPos().immutable(),
                    builder -> builder.type(MARKER).color(70, 130, 240).message("Hit " + hitCount.incrementAndGet()));
        });

        if (result != null && result.getType() != BlockHitResult.Type.MISS)
            this.addDebugElement(result.getBlockPos(),
                    builder -> builder.type(MARKER).color(255, 100, 100).message("Hit result").duration(25));

        super.renderElements(matrices, vertexConsumers, cameraX, cameraY, cameraZ);
    }

    @Override
    public void renderHud(GuiGraphics context) {
        if (!this.enabled)
            return;
        final Minecraft client = Minecraft.getInstance();
        if (client == null || client.level == null || client.player == null || client.cameraEntity == null)
            return;
        final Entity entity = client.cameraEntity;
        final EntityRaycastExtension entityRaycast = (EntityRaycastExtension) entity;
        final CollisionViewExtension world = (CollisionViewExtension) entity.level();

        final double maxDistance = 20.0;
        final BlockHitResult vanillaResult = entityRaycast.glue$vanillaRaycast(maxDistance, 0.0F, false);
        final BlockHitResult bigOutlineResult = entityRaycast.glue$bigOutlineRaycast(maxDistance, 0.0F, false);
        // pick() can return EntityHitResult — skip block-result UI when looking at an entity.
        final HitResult pick = entity.pick(maxDistance, 0.0F, false);
        final BlockHitResult result = pick instanceof BlockHitResult br ? br : null;

        final Vec3 origin = entity.getEyePosition(0.0f);
        final Vec3 rotation = entity.getViewVector(0.0f);
        final Vec3 target = origin.add(rotation.x * maxDistance, rotation.y * maxDistance, rotation.z * maxDistance);

        final List<Tuple<BlockPos, VoxelShape>> bigOutlineShapes = ImmutableList
                .copyOf(world.glue$getBlockCollisions(entity, entity.getBoundingBox().inflate(6.0))).stream()
                .filter(pair -> pair.getA() != null).toList();

        final List<String> texts = new ArrayList<>();
        texts.add("Raycast debug");
        texts.add("");

        texts.add("Origin: " + BlockPos.containing(origin).toShortString());
        texts.add("Target: " + BlockPos.containing(target).toShortString());
        texts.add("Max Range: " + maxDistance);
        texts.add("");

        if (!bigOutlineShapes.isEmpty()) {
            bigOutlineShapes.forEach(blockPosVoxelShapePair -> {
                final BlockState state = entity.level().getBlockState(blockPosVoxelShapePair.getA());
                texts.add(blockPosVoxelShapePair.getA().toShortString() + ": " + state);
            });
            texts.add("");
        }

        this.getHitsText(texts, bigOutlineShapes, origin, target);

        this.getResultText(texts, "Vanilla", vanillaResult, client.level, origin);
        this.getResultText(texts, "Big Outline", bigOutlineResult, client.level, origin);
        this.getResultText(texts, "Final Result", result, client.level, origin);

        if (result == null) {
            texts.add("Final Result is null");
            this.drawRight(context, texts, client);
            return;
        }

        boolean isEq = false;
        if (vanillaResult != null && result.getType() != BlockHitResult.Type.MISS && vanillaResult.getType() != BlockHitResult.Type.MISS) {
            isEq = result.getBlockPos().equals(vanillaResult.getBlockPos()) && result.getDirection().equals(vanillaResult.getDirection());
        } else if (vanillaResult != null && result.getType() == BlockHitResult.Type.MISS && vanillaResult.getType() == BlockHitResult.Type.MISS) {
            isEq = true;
        }

        texts.add("Eq (Pos/Dir): " + isEq);
        texts.add("Result Type: " + result.getType());
        texts.add("Final Result matches Vanilla? " + isEq);

        this.drawRight(context, texts, client);
    }

    private void getResultText(List<String> texts, String name, BlockHitResult result, Level world, Vec3 origin) {
        if (result != null && result.getType() != BlockHitResult.Type.MISS) {
            final BlockPos bigOutlinePos = result.getBlockPos();
            final BlockState bigOutlineState = world.getBlockState(bigOutlinePos);
            final double bigOutlineDistance = result.getLocation().distanceToSqr(origin);

            texts.add(name + " Pos: " + bigOutlinePos.toShortString());
            texts.add(name + " State: " + bigOutlineState);
            texts.add(name + " Distance: " + String.format("%.2f", bigOutlineDistance));
            texts.add("");
        } else {
            texts.add(name + ": MISS");
            texts.add("");
        }
    }

    private void getHitsText(List<String> texts, List<Tuple<BlockPos, VoxelShape>> bigOutlineShapes, Vec3 origin,
                             Vec3 target) {
        if (bigOutlineShapes.isEmpty())
            return;
        final AtomicInteger hitCount = new AtomicInteger(0);
        texts.add("Hits:");

        bigOutlineShapes.forEach(pair -> {
            final BlockPos blockPos = pair.getA();
            final VoxelShape shape = pair.getB();
            final BlockHitResult hit = shape.clip(origin, target, blockPos);
            if (hit == null)
                return;
            hitCount.incrementAndGet();

            final Vec3 hitPos = hit.getLocation();
            final double distance = hitPos.distanceToSqr(origin);

            texts.add("Hit " + hitCount.get() + " at: " + BlockPos.containing(hitPos).toShortString());
            texts.add("Distance: " + String.format("%.2f", distance));
        });

        if (hitCount.get() == 0)
            texts.add("No hits");
        texts.add("");
    }
}
