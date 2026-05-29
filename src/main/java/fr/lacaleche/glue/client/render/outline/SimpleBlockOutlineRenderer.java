package fr.lacaleche.glue.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class SimpleBlockOutlineRenderer implements GlueOutlineRenderer {

    private static final float OUTLINE_ALPHA = 0.4f;

    @Override
    public void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos) {
        render(client, world, voxelShape, matrices, consumers, blockPos, cameraPos, Color.BLACK);
    }

    @Override
    public void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos, Color color) {
        renderShape(voxelShape, matrices.last(), consumers.getBuffer(RenderType.LINES), color);
    }

    @Override
    public void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers) {
        renderCollisionBox(client, world, voxelShape, matrices, consumers, Color.RED);
    }

    @Override
    public void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, Color color) {
        renderShape(voxelShape, matrices.last(), consumers.getBuffer(RenderType.LINES), color);
    }

    protected void renderShape(VoxelShape voxelShape, PoseStack.Pose transform, VertexConsumer consumer, Color color) {
        voxelShape.forAllEdges((x1, y1, z1, x2, y2, z2) -> {
            float xDiff = (float) (x2 - x1);
            float yDiff = (float) (y2 - y1);
            float zDiff = (float) (z2 - z1);
            float length = Mth.sqrt(xDiff * xDiff + yDiff * yDiff + zDiff * zDiff);
            if (length < 1e-6f) return;

            xDiff /= length;
            yDiff /= length;
            zDiff /= length;

            float r = color.getRed() / 255.0f;
            float g = color.getGreen() / 255.0f;
            float b = color.getBlue() / 255.0f;

            consumer.addVertex(transform.pose(), (float) x1, (float) y1, (float) z1)
                    .setColor(r, g, b, OUTLINE_ALPHA)
                    .setNormal(transform, xDiff, yDiff, zDiff);
            consumer.addVertex(transform.pose(), (float) x2, (float) y2, (float) z2)
                    .setColor(r, g, b, OUTLINE_ALPHA)
                    .setNormal(transform, xDiff, yDiff, zDiff);
        });
    }

}
