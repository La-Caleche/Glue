package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Shader showcase renderer — renders a floating diamond sword with one of five
 * custom shader effects. Right-click the block to cycle through them.
 *
 * <ol>
 *   <li>Hologram — translucent holographic scan-line effect</li>
 *   <li>Enchanted Glow — animated enchantment shimmer</li>
 *   <li>Frozen — icy crystal overlay</li>
 *   <li>X-Ray — see-through wireframe look</li>
 *   <li>Inferno — animated fire/lava distortion</li>
 * </ol>
 */
public class TestShaderBlockEntityRenderer implements BlockEntityRenderer<TestShaderBlockEntity> {

    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.DIAMOND_SWORD);

    private static final String[] SHADER_NAMES = {
            "hologram", "enchanted_glow", "frozen", "xray", "inferno"
    };

    private static GluePipeline[] pipelines;

    private static GluePipeline[] getPipelines() {
        if (pipelines == null) {
            pipelines = new GluePipeline[SHADER_NAMES.length];
            for (int i = 0; i < SHADER_NAMES.length; i++) {
                String name = SHADER_NAMES[i];
                pipelines[i] = GluePipeline.entity(
                        TestmodClient.id(name),
                        TestmodClient.id("core/" + name),
                        TestmodClient.id("core/" + name)
                );
            }
        }
        return pipelines;
    }

    /** Returns the display name for the given shader index. */
    public static String getShaderName(int index) {
        return SHADER_NAMES[index % SHADER_NAMES.length];
    }

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

        int shaderIndex = entity.getShaderIndex();
        GluePipeline activePipeline = getPipelines()[shaderIndex % getPipelines().length];

        poseStack.pushPose();
        poseStack.translate(0.5, 2.5, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(2 * 45f));

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

