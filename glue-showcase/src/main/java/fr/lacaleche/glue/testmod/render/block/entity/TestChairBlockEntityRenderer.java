package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.transform.GlueTransformStack;
import fr.lacaleche.glue.testmod.blocks.demo.TestChairBlock;
import fr.lacaleche.glue.testmod.blocks.demo.TickingBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Draws the chair's block model turned by its 16-step rotation, as Occamod does. */
public class TestChairBlockEntityRenderer implements BlockEntityRenderer<TickingBlockEntity> {

    public TestChairBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(TickingBlockEntity entity, float tickDelta, PoseStack matrices, MultiBufferSource buffers,
                       int light, int overlay, Vec3 cameraPos) {
        BlockState state = entity.getBlockState();
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        float degrees = RotationSegment.convertToDegrees(state.getValue(TestChairBlock.ROTATION));

        GlueTransformStack.of(matrices).pushPose()
                .rotateCentered((float) Math.toRadians(-degrees), Direction.UP)
                .then(() -> {
                    List<BlockModelPart> parts = new ArrayList<>();
                    dispatcher.getBlockModel(state).collectParts(RandomSource.create(42L), parts);
                    dispatcher.getModelRenderer().tesselateBlock(entity.getLevel(), parts, state, entity.getBlockPos(),
                            matrices, buffers.getBuffer(RenderType.cutout()), true, OverlayTexture.NO_OVERLAY);
                })
                .popPose();
    }
}
