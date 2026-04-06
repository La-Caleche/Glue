package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.transform.GlueTransformStack;
import fr.lacaleche.glue.testmod.blocks.debug.TestDebugBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TestDebugBlockEntityRenderer implements BlockEntityRenderer<TestDebugBlockEntity> {

    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.SEA_LANTERN);

    private final ItemRenderer itemRenderer;

    public TestDebugBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(TestDebugBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay, Vec3 cameraPos) {
        float delta = (float) entity.getTicks() / 5;
        float rotation = delta + tickDelta / 20.0F;
        float yTranslation = (float) Math.sin(delta / 10) / 10;

        GlueTransformStack stack = GlueTransformStack.of(matrices);
        stack.pushPose()
                .rotateCentered((float) Math.toRadians(-rotation), Direction.UP)
                .translate(.5, 0.3 + yTranslation, .5);

        itemRenderer.renderStatic(DISPLAY_ITEM, ItemDisplayContext.GROUND, light, overlay,
                matrices, vertexConsumers, entity.getLevel(), (int) entity.getBlockPos().asLong());

        stack.popPose();
    }
}
