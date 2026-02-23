package fr.lacaleche.glue.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.client.events.DebugEvents;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.transform.GlueTransformStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;

public class BlockRenderer {

    public static VoxelShape getBreakParticleShape(BlockState blockState, ClientLevel world, BlockGetter blockView,
            BlockPos blockPos) {
        if (!(blockState.getBlock() instanceof GlueBlock))
            return null;
        return blockState.getBlock().getBlockSupportShape(blockState, blockView, blockPos);
    }

    public static boolean drawBlockOutline(Minecraft client, Level world, Vec3 camera, HitResult hitResult,
            PoseStack matrices, MultiBufferSource buffers) {
        if (!(hitResult instanceof BlockHitResult target) || world == null || client.player == null)
            return false;

        final BlockPos pos = target.getBlockPos();
        final BlockState blockstate = world.getBlockState(pos);
        final Block block = blockstate.getBlock();

        if (!world.getWorldBorder().isWithinBounds(pos) || !(blockstate.getBlock() instanceof GlueBlock))
            return false;

        final VoxelShape shape = blockstate.getShape(world, pos, CollisionContext.of(client.player));

        final GlueOutlineRenderer renderer = GlueOutlineRenderers.getOutlineRenderers().keySet().stream()
                .filter(r -> GlueOutlineRenderers.getOutlineRenderers().get(r).contains(block))
                .findFirst().orElse(GlueOutlineRenderers.BASE_OUTLINE);

        DebugEvents.BLOCK_OUTLINE.invoker().onRenderBlockOutline(client, world, pos, blockstate, camera, hitResult,
                matrices, buffers);

        float rotation = .0f;
        if (blockstate.getProperties().contains(BlockStateProperties.ROTATION_16)) {
            rotation = RotationSegment.convertToDegrees(blockstate.getValue(BlockStateProperties.ROTATION_16));
        } else if (blockstate.getProperties().contains(HorizontalDirectionalBlock.FACING)) {
            int rotationDirection = RotationSegment
                    .convertToSegment(blockstate.getValue(HorizontalDirectionalBlock.FACING).getOpposite());
            rotation = RotationSegment.convertToDegrees(rotationDirection);
        }

        GlueTransformStack.of(matrices).pushPose()
                .translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z)
                .rotateCentered((float) Math.toRadians(-rotation), Direction.UP)
                .then(() -> renderer.render(client, world, shape, matrices, buffers, pos, camera, Color.BLACK))
                .popPose();

        return true;
    }

}
