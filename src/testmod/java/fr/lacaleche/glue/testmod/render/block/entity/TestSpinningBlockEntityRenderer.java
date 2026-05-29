package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.client.transform.GlueTransformStack;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestSpinningBlockEntity;
import fr.lacaleche.glue.testmod.render.TestShaderPipelines;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class TestSpinningBlockEntityRenderer implements BlockEntityRenderer<TestSpinningBlockEntity> {

    private static final ItemStack NETHER_STAR = new ItemStack(Items.NETHER_STAR);
    private static final ItemStack AMETHYST = new ItemStack(Items.AMETHYST_SHARD);

    private static final float STAR_HEIGHT = 2.2f;
    private static final float AMETHYST_HEIGHT = 1.75f;
    private static final float ORBIT_RADIUS = 1f;
    private static final float GROUP_SPIN_SPEED = 40f;
    private static final float STAR_SPIN_SPEED = 60f;
    private static final float BOB_SPEED = 1.5f;
    private static final float BOB_AMP = 0.05f;

    private static final int SHARD_COUNT = 4;
    private static final float SHARD_SCALE = 0.5f;

    private final ItemRenderer itemRenderer;

    public TestSpinningBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    private static float pseudoRandom(long seed, int index) {
        long h = seed * 6364136223846793005L + index * 1442695040888963407L;
        h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
        return (float) ((h >>> 40) & 0xFFFFF) / (float) 0xFFFFF;
    }

    @Override
    public void render(TestSpinningBlockEntity entity, float tickDelta, PoseStack matrices,
                       MultiBufferSource vertexConsumers, int light, int overlay, Vec3 cameraPos) {
        if (RenderCompat.isRenderingShadowPass()) return;

        GluePipeline activePipeline = TestShaderPipelines.get(2);

        float time = (entity.getTicks() + tickDelta) / 20f;
        float globalBob = (float) Math.sin(time * BOB_SPEED) * BOB_AMP;
        long seed = entity.getBlockPos().asLong();
        GlueTransformStack stack = GlueTransformStack.of(matrices);

        ShadedBufferSource shadedSource = activePipeline.wrap();

        stack.pushPose()
                .translate(0.5, STAR_HEIGHT + globalBob, 0.5)
                .rotateYDegrees(time * STAR_SPIN_SPEED)
                .scale(0.8f, 0.8f, 0.8f)
                .then(() -> itemRenderer.renderStatic(NETHER_STAR, ItemDisplayContext.FIXED,
                        light, overlay, matrices, vertexConsumers, entity.getLevel(), 0))
                .popPose();

        stack.pushPose()
                .translate(0.5, AMETHYST_HEIGHT + (globalBob * 0.5f), 0.5)
                .rotateYDegrees(time * GROUP_SPIN_SPEED);

        for (int i = 0; i < SHARD_COUNT; i++) {
            float angle = i * (360f / SHARD_COUNT);
            float rng = pseudoRandom(seed, i);

            float selfSpeed = 20f + rng * 40f;
            float tiltX = 10f + rng * 30f;
            float tiltZ = (rng - 0.5f) * 20f;
            float bobOffset = rng * 6.28f;
            float shardBob = (float) Math.sin(time * (1.0f + rng) + bobOffset) * 0.04f;

            stack.pushPose()
                    .rotateYDegrees(angle)
                    .translate(ORBIT_RADIUS, shardBob, 0)
                    .rotateXDegrees(tiltX)
                    .rotateZDegrees(tiltZ)
                    .rotateYDegrees(time * selfSpeed)
                    .scale(SHARD_SCALE, SHARD_SCALE, SHARD_SCALE)
                    .then(() -> itemRenderer.renderStatic(AMETHYST, ItemDisplayContext.FIXED,
                            light, overlay, matrices, shadedSource, entity.getLevel(), 0))
                    .popPose();
        }

        shadedSource.endBatch();
        stack.popPose();
    }
}