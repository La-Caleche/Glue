package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lacaleche.glue.client.shader.ShaderRenderer;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/**
 * Renders an animated floating quad above the TestShaderBlock using a custom shader pipeline.
 * <p>
 * Demonstrates world-space shader rendering with:
 * <ul>
 *   <li>Time-based rotation animation</li>
 *   <li>Per-vertex gradient colors that cycle over time</li>
 *   <li>Translucent blending</li>
 * </ul>
 */
public class TestShaderBlockEntityRenderer implements BlockEntityRenderer<TestShaderBlockEntity> {

    public TestShaderBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    /**
     * Must return true because the quad renders 1.5+ blocks above the base block.
     * Without this, MC culls the block entity when the block itself is off-screen
     * (e.g. when looking up at the floating quad from close range).
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public void render(TestShaderBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay, Vec3 cameraPos) {
        // Skip during Iris shadow pass — raw GL draws would create ghost quads
        // at incorrect positions in the shadow map
        if (RenderCompat.isRenderingShadowPass()) return;
        float time = (entity.getTicks() + partialTick) / 20f;
        float progress = entity.getAnimationProgress();

        poseStack.pushPose();

        // Float above the block
        poseStack.translate(0.5, 1.5 + Math.sin(time * 2) * 0.15, 0.5);

        // Slow rotation around Y axis
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 45f));

        // Slight tilt for visual flair
        poseStack.mulPose(Axis.XP.rotationDegrees(15f));

        // Compute time-cycling gradient colors
        float hueShift = progress;
        float r1 = hsvR(hueShift);
        float g1 = hsvG(hueShift);
        float b1 = hsvB(hueShift);

        float r2 = hsvR(hueShift + 0.25f);
        float g2 = hsvG(hueShift + 0.25f);
        float b2 = hsvB(hueShift + 0.25f);

        float r3 = hsvR(hueShift + 0.5f);
        float g3 = hsvG(hueShift + 0.5f);
        float b3 = hsvB(hueShift + 0.5f);

        float r4 = hsvR(hueShift + 0.75f);
        float g4 = hsvG(hueShift + 0.75f);
        float b4 = hsvB(hueShift + 0.75f);

        // Render the quad using raw GL via ShaderRenderer (Iris-compatible)
        ShaderRenderer.world()
                .matrix(poseStack.last().pose())
                .position(-0.5f, -0.5f, 0f)
                .size(1f, 1f)
                .cornerColors(
                        r1, g1, b1, 0.85f,
                        r2, g2, b2, 0.85f,
                        r3, g3, b3, 0.85f,
                        r4, g4, b4, 0.85f
                )
                .draw(bufferSource);

        poseStack.popPose();
    }

    // Simple HSV-to-RGB conversion for rainbow cycling (saturation=1, value=1)
    private static float hsvR(float h) {
        h = ((h % 1f) + 1f) % 1f;
        float k = (5f + h * 6f) % 6f;
        return 1f - Math.max(0, Math.min(Math.min(k, 4f - k), 1f));
    }

    private static float hsvG(float h) {
        h = ((h % 1f) + 1f) % 1f;
        float k = (3f + h * 6f) % 6f;
        return 1f - Math.max(0, Math.min(Math.min(k, 4f - k), 1f));
    }

    private static float hsvB(float h) {
        h = ((h % 1f) + 1f) % 1f;
        float k = (1f + h * 6f) % 6f;
        return 1f - Math.max(0, Math.min(Math.min(k, 4f - k), 1f));
    }
}
