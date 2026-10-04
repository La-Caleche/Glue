package fr.lacaleche.composite.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.composite.CompositeBlockEntity;
import fr.lacaleche.composite.CompositePart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Draws each part's block entity with its own renderer, moved by the part's transform. */
public final class CompositeBlockEntityRenderer implements BlockEntityRenderer<CompositeBlockEntity> {

    private final BlockEntityRenderDispatcher dispatcher;

    public CompositeBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.dispatcher = context.getBlockEntityRenderDispatcher();
    }

    @Override
    public void render(CompositeBlockEntity cell, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int light, int overlay, Vec3 cameraPos) {
        List<CompositePart> parts = cell.parts();
        for (int i = 0; i < parts.size(); i++) {
            BlockEntity entity = cell.entity(i);
            if (entity != null) render(entity, parts.get(i), partialTick, poseStack, buffers, light, overlay, cameraPos);
        }
    }

    /** Parts may reach a block past their cell, out of the frustum test of the cell's own box. */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    private <E extends BlockEntity> void render(E entity, CompositePart part, float partialTick, PoseStack poseStack,
                                                MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos) {
        BlockEntityRenderer<E> renderer = this.dispatcher.getRenderer(entity);
        if (renderer == null || !renderer.shouldRender(entity, cameraPos)) return;
        poseStack.pushPose();
        poseStack.mulPose(part.matrix());
        renderer.render(entity, partialTick, poseStack, buffers, light, overlay, cameraPos);
        poseStack.popPose();
    }
}
