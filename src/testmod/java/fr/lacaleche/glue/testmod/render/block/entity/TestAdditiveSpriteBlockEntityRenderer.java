package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestAdditiveSpriteBlockEntity;
import fr.lacaleche.glue.testmod.render.AdditiveSpriteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renderer for the additive sprite block entity.
 *
 * <p>
 * Renders the {@code particle01.png} texture as a camera-facing (billboard)
 * quad using additive blending. The pipeline is not registered with Iris,
 * so our custom shader runs directly under both vanilla and Iris.
 * </p>
 */
public class TestAdditiveSpriteBlockEntityRenderer implements BlockEntityRenderer<TestAdditiveSpriteBlockEntity> {

    private static final ResourceLocation SPRITE_TEXTURE =
            TestmodClient.id("textures/imported/particle01.png");

    /** Full-bright packed light value (sky=15, block=15). */
    private static final int FULL_BRIGHT = 15728880;

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

        float time = (entity.getTicks() + partialTick) / 20f;

        // Render directly to the buffer source with our custom pipeline.
        // No Iris program is assigned, so our shader compiles and runs as-is.
        RenderType renderType = AdditiveSpriteRenderer.getRenderType(SPRITE_TEXTURE);
        VertexConsumer consumer = bufferSource.getBuffer(renderType);

        // Pulse scale: gently breathes between 0.85x and 1.15x
        float pulse = 1.0f + 0.15f * (float) Math.sin(time * 2.5);
        float size = 0.75f * pulse;

        // Floating bob
        float bob = (float) Math.sin(time * 1.5) * 0.1f;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.8 + bob, 0.5);

        // Billboard: camera rotation quaternion
        poseStack.mulPose(Minecraft.getInstance().gameRenderer.getMainCamera().rotation());

        poseStack.scale(size, size, size);

        Matrix4f pose = poseStack.last().pose();

        float halfW = 0.5f;
        float halfH = 0.5f;
        int r = 255, g = 255, b = 255, a = 255;

        // Emit the quad
        vertex(consumer, pose, -halfW, -halfH, 0, 0f, 1f, r, g, b, a, FULL_BRIGHT);
        vertex(consumer, pose,  halfW, -halfH, 0, 1f, 1f, r, g, b, a, FULL_BRIGHT);
        vertex(consumer, pose,  halfW,  halfH, 0, 1f, 0f, r, g, b, a, FULL_BRIGHT);
        vertex(consumer, pose, -halfW,  halfH, 0, 0f, 0f, r, g, b, a, FULL_BRIGHT);

        poseStack.popPose();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose,
                               float x, float y, float z,
                               float u, float v,
                               int r, int g, int b, int a,
                               int packedLight) {
        consumer.addVertex(pose, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(0f, 1f, 0f);
    }
}
