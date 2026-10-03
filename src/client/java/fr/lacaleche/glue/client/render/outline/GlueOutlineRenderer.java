package fr.lacaleche.glue.client.render.outline;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.math.Color;
import fr.lacaleche.glue.shaper.PlacedGeometry;
import fr.lacaleche.glue.shaper.ShapeGeometry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

import java.util.List;

public interface GlueOutlineRenderer {

    void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos);

    void render(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos, Color color);

    void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers);

    void renderCollisionBox(Minecraft client, Level world, VoxelShape voxelShape, PoseStack matrices, MultiBufferSource consumers, Color color);

    /**
     * Outlines a block's geometry exactly, at any angle: each axis-aligned part of each placed
     * geometry goes through {@link #render(Minecraft, Level, VoxelShape, PoseStack, MultiBufferSource, BlockPos, Vec3, Color)}
     * under the matrix that places it, so a renderer that draws a voxel shape draws geometry too.
     */
    default void render(Minecraft client, Level world, List<PlacedGeometry> geometry, PoseStack matrices,
                        MultiBufferSource consumers, BlockPos blockPos, Vec3 cameraPos, Color color) {
        for (PlacedGeometry placed : geometry) {
            for (ShapeGeometry.AlignedPart part : placed.geometry().alignedParts()) {
                matrices.pushPose();
                matrices.mulPose(new Matrix4f(placed.transform()).mul(part.matrix()));
                render(client, world, part.shape(), matrices, consumers, blockPos, cameraPos, color);
                matrices.popPose();
            }
        }
    }

}
