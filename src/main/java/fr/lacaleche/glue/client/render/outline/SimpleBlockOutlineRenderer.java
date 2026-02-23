package fr.lacaleche.glue.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;

public class SimpleBlockOutlineRenderer implements GlueOutlineRenderer {

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

            xDiff /= length;
            yDiff /= length;
            zDiff /= length;

            int r = color.getRed() / 255;
            int g = color.getGreen() / 255;
            int b = color.getBlue() / 255;

            consumer.addVertex(transform.pose(), (float) x1, (float) y1, (float) z1)
                    .setColor(r, g, b, .4f)
                    .setNormal(transform, xDiff, yDiff, zDiff);
            consumer.addVertex(transform.pose(), (float) x2, (float) y2, (float) z2)
                    .setColor(r, g, b, .4f)
                    .setNormal(transform, xDiff, yDiff, zDiff);
        });
    }

}
