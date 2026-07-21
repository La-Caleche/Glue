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
 * Renderer for the additive sprite block entity: a camera-facing, additively-blended glow that
 * bobs and pulses above the block, drawn through a data-driven {@link GluePipeline}.
 *
 * <p>The pipeline's {@link ShadedBufferSource} captures the sprite and blits it additively
 * ({@code GL_ONE, GL_ONE}) so it composites correctly under both vanilla and Iris. The bob height
 * comes from {@link TestAdditiveSpriteBlockEntity#spriteCenterY}, the same value its attached Lumos
 * light follows, so the light and the visible glow stay locked together.</p>
 */
public class TestAdditiveSpriteBlockEntityRenderer implements BlockEntityRenderer<TestAdditiveSpriteBlockEntity> {

    private static final ResourceLocation SPRITE_TEXTURE =
            TestmodClient.id("textures/imported/particle01.png");

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

        GluePipeline pipeline = AdditiveSpriteRenderer.getPipeline();
        RenderType renderType = pipeline.renderType(SPRITE_TEXTURE);

        float half = 0.5f * 0.75f * entity.pulse(partialTick);
        Matrix4f pose = poseStack.last().pose();
        sprite.facingCamera(Minecraft.getInstance().gameRenderer.getMainCamera());

        try (ShadedBufferSource shadedSource = pipeline.wrap()) {
            sprite.emit(shadedSource.getBuffer(renderType), pose,
                    0.5f, (float) entity.spriteCenterY(partialTick), 0.5f, half, half,
                    255, 125, 185, 255, LightTexture.FULL_BRIGHT);
            shadedSource.endBatch();
        }
    }
}
