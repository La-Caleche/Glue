package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

/**
 * Re-rasterises nearby metal blocks from the camera's point of view into the shared material G-buffer
 * under the {@code METAL} material id (6), so the deferred and reflection passes give them a metallic
 * BRDF (albedo-tinted specular + environment reflection) rather than the diffuse terrain response.
 *
 * <p>Metals are opaque model blocks, so unlike water this reuses the glass {@code renderSingleBlock}
 * path directly &mdash; a per-block pose translate into the shared buffer. Vanilla already drew the
 * block into the main colour; this overwrites only the material attachments (id/albedo/props) where a
 * metal block is the frontmost surface, replacing the terrain id it was captured with.</p>
 */
@Environment(EnvType.CLIENT)
public final class MetalSceneRenderer extends AbstractSceneRenderer {

    /** Curated set of blocks Lumos treats as polished metal. Vanilla carries no PBR data, so this is
     *  the deliberate data source; edit here to add or remove metals. */
    private static final Set<Block> METALS = Set.of(
            Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.NETHERITE_BLOCK,
            Blocks.COPPER_BLOCK, Blocks.EXPOSED_COPPER, Blocks.WEATHERED_COPPER, Blocks.OXIDIZED_COPPER,
            Blocks.CUT_COPPER, Blocks.EXPOSED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER,
            Blocks.WAXED_COPPER_BLOCK, Blocks.WAXED_EXPOSED_COPPER, Blocks.WAXED_WEATHERED_COPPER,
            Blocks.WAXED_OXIDIZED_COPPER, Blocks.WAXED_CUT_COPPER, Blocks.WAXED_EXPOSED_CUT_COPPER,
            Blocks.WAXED_WEATHERED_CUT_COPPER, Blocks.WAXED_OXIDIZED_CUT_COPPER,
            Blocks.RAW_IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.RAW_COPPER_BLOCK,
            Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
            Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE);

    public static boolean isMetal(BlockState state) {
        return METALS.contains(state.getBlock());
    }

    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<BlockPos> blocks = List.of();

    public void configure(Matrix4f view, Matrix4f proj,
                          double camX, double camY, double camZ, List<BlockPos> blocks) {
        this.view = view;
        this.proj = proj;
        this.camX = camX;
        this.camY = camY;
        this.camZ = camZ;
        this.blocks = blocks;
    }

    @Override
    protected Matrix4f createProjectionMatrix(int width, int height) {
        return proj;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return view;
    }

    @Override
    protected float getScale() {
        return 1.0f;
    }

    @Override
    protected PoseStack buildModelViewMatrix(Matrix4f viewMatrix, float scale) {
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        return matrices;
    }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null || blocks.isEmpty()) return;
        if (!GBufferCapture.beginMetalCapture()) return;
        try {
            RenderType type = ShadowPipelines.metalGBuffer();
            BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
            MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
            MultiBufferSource redirect = ignored -> bufferSource.getBuffer(type);

            for (BlockPos pos : blocks) {
                matrices.pushPose();
                matrices.translate(
                        (float) (pos.getX() - camX),
                        (float) (pos.getY() - camY),
                        (float) (pos.getZ() - camZ));
                blockRenderer.renderSingleBlock(client.level.getBlockState(pos), matrices, redirect,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                matrices.popPose();
            }

            bufferSource.endBatch(type);
        } finally {
            GBufferCapture.endMetalCapture();
        }
    }
}
