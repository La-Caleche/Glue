package fr.lacaleche.glue.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;

public interface GlueOutlineRenderer {

    void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos);

    void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos, Color color);

    void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers);

    void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, Color color);

}
