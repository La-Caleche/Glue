package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.transform.GlueTransformStack;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TestSpinningBlockEntityRenderer implements BlockEntityRenderer<TestSpinningBlockEntity> {

    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.DIAMOND_SWORD);

    private final ItemRenderer itemRenderer;

    public TestSpinningBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(TestSpinningBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay, Vec3 cameraPos) {
        entity.tick();

        float time = entity.getTicks() + tickDelta;

        GlueTransformStack.of(matrices).pushPose()
                .rotateCentered((float) Math.toRadians(time * 2), Direction.UP)
                .rotateCentered((float) Math.toRadians(15), Direction.EAST)
                .translate(0.8, 1.75, -0.5)
                .translate(0, Math.sin(time * 0.025) * 0.1, 0)
                .then(() -> {
                    this.itemRenderer.renderStatic(
                            DISPLAY_ITEM,
                            ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                            light,
                            overlay,
                            matrices,
                            vertexConsumers,
                            entity.getLevel(),
                            0);
                })
                .popPose();
    }
}
