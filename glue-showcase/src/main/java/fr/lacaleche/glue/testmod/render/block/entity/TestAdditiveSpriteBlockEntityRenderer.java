package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.BillboardSprite;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlockEntity;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renderer for the additive sprite block entity: a cluster of overlapping, camera-facing glows that
 * bob and pulse above the block, drawn in one {@link ShadedBufferSource}.
 *
 * <p>The pipeline does not write depth, so the sprites add up whatever their order and their dark
 * texels never hide one another. The bob height comes from {@link TestAdditiveSpriteBlockEntity#spriteCenterY}.</p>
 */
public class TestAdditiveSpriteBlockEntityRenderer implements BlockEntityRenderer<TestAdditiveSpriteBlockEntity> {

    private static final ResourceLocation SPRITE_TEXTURE =
            TestmodClient.id("textures/imported/particle01.png");
    private static final float SPRITE_HALF_SIZE = 0.3f;
    /** Each sprite's offset from its cluster's centre, as {x, y, z}: overlapping, at different depths. */
    private static final float[][] CLUSTER_SPRITES = {
            {0f, 0f, -0.3f},
            {0.15f, 0.1f, 0f},
            {-0.12f, -0.08f, 0.3f},
    };

    private final BillboardSprite sprite = new BillboardSprite();

    public TestAdditiveSpriteBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void render(TestAdditiveSpriteBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        if (RenderCompat.isRenderingShadowPass()) return;

        float half = SPRITE_HALF_SIZE * entity.pulse(partialTick);
        float centerY = (float) entity.spriteCenterY(partialTick);
        Matrix4f pose = poseStack.last().pose();
        sprite.facingCamera(Minecraft.getInstance().gameRenderer.getMainCamera());

        drawCluster(AdditiveSpriteRenderer.getPipeline(), pose, 0.5f, centerY, half, 1.0f);
    }

    private void drawCluster(GluePipeline pipeline, Matrix4f pose, float centerX, float centerY, float half, float colorMultiplier) {
        RenderType renderType = pipeline.renderType(SPRITE_TEXTURE);
        try (ShadedBufferSource shadedSource = pipeline.wrap()) {
            for (float[] offset : CLUSTER_SPRITES) {
                sprite.emit(shadedSource.getBuffer(renderType), pose,
                        centerX + offset[0], centerY + offset[1], 0.5f + offset[2], half, half,
                        (int) (255 * colorMultiplier), (int) (125 * colorMultiplier), (int) (185 * colorMultiplier), 255, LightTexture.FULL_BRIGHT);
            }
        }
    }
}
