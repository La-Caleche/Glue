package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import fr.lacaleche.glue.testmod.render.TestShaderPipelines;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TestShaderBlockEntityRenderer implements BlockEntityRenderer<TestShaderBlockEntity> {

    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.DIAMOND_SWORD);

    private final ItemRenderer itemRenderer;

    public TestShaderBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void render(TestShaderBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        if (RenderCompat.isRenderingShadowPass()) return;

        float time = (entity.getTicks() + partialTick) / 20f;
        int shaderIndex = entity.getShaderIndex();
        GluePipeline[] pipelines = TestShaderPipelines.get();
        GluePipeline activePipeline = pipelines[shaderIndex % pipelines.length];

        poseStack.pushPose();
        poseStack.translate(0.5, 2.1 + Math.sin(time * 2) * 0.15, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 45f));
        poseStack.mulPose(Axis.XP.rotationDegrees(15f));

        ShadedBufferSource shadedSource = activePipeline.wrap(bufferSource);

        itemRenderer.renderStatic(
                DISPLAY_ITEM,
                ItemDisplayContext.FIXED,
                packedLight,
                packedOverlay,
                poseStack,
                shadedSource,
                entity.getLevel(),
                0);

        shadedSource.endBatch();
        poseStack.popPose();
    }
}
