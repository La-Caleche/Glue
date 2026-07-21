package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.light.internal.LumosBuffers;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BlockModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Re-rasterises nearby base terrain from the camera's point of view into the shared material
 * G-buffer under the {@code TERRAIN} material id (1) &mdash; the reduced-capture stand-in for the
 * native terrain capture, which an Iris shaderpack frame blocks (the pack owns every terrain
 * program). Only runs on those frames; the vanilla and Sodium paths capture terrain in the scene's
 * own draws.
 *
 * <p>Blocks render through {@code renderBatched} rather than the glass/metal
 * {@code renderSingleBlock} path so biome tints, per-face culling against real neighbours and the
 * per-position model seed all match what the pack drew; the fragment stage then packs the real
 * albedo and normal exactly like the native capture, so the composite's lighting is identical
 * whichever path captured the pixel.</p>
 */
@Environment(EnvType.CLIENT)
public final class TerrainSceneRenderer extends AbstractSceneRenderer {

    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<BlockPos> blocks = List.of();
    private final RandomSource random = RandomSource.create();

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
        if (!GBufferCapture.beginTerrainRecapture()) return;
        try {
            RenderType type = ShadowPipelines.terrainGBuffer();
            BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
            MultiBufferSource.BufferSource bufferSource = LumosBuffers.source();
            VertexConsumer consumer = bufferSource.getBuffer(type);

            for (BlockPos pos : blocks) {
                BlockState state = client.level.getBlockState(pos);
                if (state.getRenderShape() != RenderShape.MODEL) continue;
                random.setSeed(state.getSeed(pos));
                List<BlockModelPart> parts =
                        blockRenderer.getBlockModel(state).collectParts(random);
                matrices.pushPose();
                matrices.translate(
                        (float) (pos.getX() - camX),
                        (float) (pos.getY() - camY),
                        (float) (pos.getZ() - camZ));
                blockRenderer.renderBatched(state, pos, client.level, matrices, consumer,
                        true, parts);
                matrices.popPose();
            }

            bufferSource.endBatch(type);
        } finally {
            GBufferCapture.endTerrainRecapture();
        }
    }
}
