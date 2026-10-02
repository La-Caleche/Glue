package fr.lacaleche.glue.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.block.GlueBlock;
import fr.lacaleche.glue.client.events.DebugEvents;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import fr.lacaleche.glue.client.registries.GlueOutlineRenderers;
import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.math.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

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

        if (!world.getWorldBorder().isWithinBounds(pos) || !(blockstate.getBlock() instanceof GlueBlock glueBlock))
            return false;

        final VoxelShape shape = blockstate.getShape(world, pos, CollisionContext.of(client.player));

        // ReloadableRegistry handles both JSON and Java entries — single lookup
        GlueOutlineRenderer resolved = GlueClientRegistries.OUTLINE_RENDERERS.get(glueBlock.getOutlineRenderer());
        final GlueOutlineRenderer renderer = resolved != null ? resolved : GlueOutlineRenderers.BASE_OUTLINE;

        DebugEvents.BLOCK_OUTLINE.invoker().onRenderBlockOutline(client, world, pos, blockstate, camera, hitResult,
                matrices, buffers);

        matrices.pushPose();
        try {
            matrices.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            renderer.render(client, world, shape, matrices, buffers, pos, camera, Color.BLACK);
        } finally {
            matrices.popPose();
        }

        return true;
    }

}
