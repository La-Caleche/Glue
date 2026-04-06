package fr.lacaleche.glue.testmod.render.block.entity;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShadedBufferSource;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Pipeline transparency diagnostic — registers flat-black shader with
 * 6 different pipeline configurations to identify which renders opaque.
 *
 * <p>Right-click to cycle. Action bar shows config name.</p>
 * <ol>
 *   <li>ENTITIES_TRANSLUCENT + Translucent blend</li>
 *   <li>ENTITIES_TRANSLUCENT + No blend (opaque)</li>
 *   <li>ENTITIES + Translucent blend</li>
 *   <li>ENTITIES + No blend (opaque)</li>
 *   <li>BLOCK + Translucent blend</li>
 *   <li>BLOCK + No blend (opaque)</li>
 * </ol>
 */
public class TestShaderBlockEntityRenderer implements BlockEntityRenderer<TestShaderBlockEntity> {

    private static final ItemStack DISPLAY_ITEM = new ItemStack(Items.DIAMOND_SWORD);

    private static final ResourceLocation BLACK_VERT = ResourceLocation.fromNamespaceAndPath("glue-test", "core/flat_black");
    private static final ResourceLocation BLACK_FRAG = ResourceLocation.fromNamespaceAndPath("glue-test", "core/flat_black");

    private static GluePipeline[] pipelines;

    private static GluePipeline[] getPipelines() {
        if (pipelines == null) {
            pipelines = new GluePipeline[] {
                // 0: ENTITIES_TRANSLUCENT + TRANSLUCENT blend (current default)
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_et_tb"),
                        BLACK_VERT, BLACK_FRAG,
                        BlendFunction.TRANSLUCENT, "ENTITIES_TRANSLUCENT"
                ),
                // 1: ENTITIES_TRANSLUCENT + NO blend
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_et_nb"),
                        BLACK_VERT, BLACK_FRAG,
                        null, "ENTITIES_TRANSLUCENT"
                ),
                // 2: ENTITIES + TRANSLUCENT blend
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_e_tb"),
                        BLACK_VERT, BLACK_FRAG,
                        BlendFunction.TRANSLUCENT, "ENTITIES"
                ),
                // 3: ENTITIES + NO blend
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_e_nb"),
                        BLACK_VERT, BLACK_FRAG,
                        null, "ENTITIES"
                ),
                // 4: BLOCK + TRANSLUCENT blend
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_b_tb"),
                        BLACK_VERT, BLACK_FRAG,
                        BlendFunction.TRANSLUCENT, "BLOCK"
                ),
                // 5: BLOCK + NO blend
                GluePipeline.entityCustom(
                        ResourceLocation.fromNamespaceAndPath("glue-test", "black_b_nb"),
                        BLACK_VERT, BLACK_FRAG,
                        null, "BLOCK"
                )
            };
        }
        return pipelines;
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
        GluePipeline activePipeline = getPipelines()[shaderIndex];

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
