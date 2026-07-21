package fr.lacaleche.glue.client.render.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

public class BlockSceneRenderer extends AbstractSceneRenderer {

    public enum CoordinateMode {
        RELATIVE,
        /**
         * Places blocks at their absolute world coordinates. Only useful when the pivot is near the origin.
         */
        ABSOLUTE
    }

    private int halfExtentX = 5;
    private int halfExtentZ = 5;
    private int minY = -2;
    private int maxY = 3;

    private BlockPos centerPos;

    private CoordinateMode coordinateMode = CoordinateMode.RELATIVE;

    private Matrix4f viewMatrix = new Matrix4f();
    private float scale = 1.0f;

    public int getHalfExtentX() {
        return halfExtentX;
    }

    public void setHalfExtentX(int halfExtentX) {
        this.halfExtentX = halfExtentX;
    }

    public int getHalfExtentZ() {
        return halfExtentZ;
    }

    public void setHalfExtentZ(int halfExtentZ) {
        this.halfExtentZ = halfExtentZ;
    }

    public int getMinY() {
        return minY;
    }

    public void setMinY(int minY) {
        this.minY = minY;
    }

    public int getMaxY() {
        return maxY;
    }

    public void setMaxY(int maxY) {
        this.maxY = maxY;
    }

    public BlockPos getCenterPos() {
        return centerPos;
    }

    public void setCenterPos(BlockPos centerPos) {
        this.centerPos = centerPos;
    }

    public CoordinateMode getCoordinateMode() {
        return coordinateMode;
    }

    public void setCoordinateMode(CoordinateMode coordinateMode) {
        this.coordinateMode = coordinateMode;
    }

    public void setViewMatrix(Matrix4f viewMatrix) {
        this.viewMatrix = viewMatrix;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return viewMatrix;
    }

    @Override
    protected float getScale() {
        return scale;
    }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null) return;

        BlockPos center = centerPos;
        if (center == null) {
            if (client.player == null) return;
            center = client.player.getOnPos();
        }

        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();

        for (int x = -halfExtentX; x <= halfExtentX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = -halfExtentZ; z <= halfExtentZ; z++) {
                    BlockPos worldPos = center.offset(x, y, z);
                    BlockState blockState = client.level.getBlockState(worldPos);

                    if (!shouldRenderBlock(worldPos, blockState)) continue;

                    matrices.pushPose();
                    renderBlock(matrices, worldPos, blockState, x, y, z);
                    blockRenderer.renderSingleBlock(
                            blockState,
                            matrices,
                            bufferSource,
                            LightTexture.FULL_BRIGHT,
                            OverlayTexture.NO_OVERLAY);
                    matrices.popPose();
                }
            }
        }

        bufferSource.endBatch();
        renderExtras(client, matrices, center, bufferSource);
    }

    /**
     * Called after all blocks have been rendered.
     * Override to add extra geometry (entities, particles, overlays) into the same FBO pass.
     *
     * @param center       The world-space center used to fetch blocks this frame.
     * @param bufferSource Buffer source, already flushed — call endBatch() again after drawing.
     */
    protected void renderExtras(Minecraft client, PoseStack matrices, BlockPos center,
                                MultiBufferSource.BufferSource bufferSource) {
    }

    /**
     * Override to change which blocks are included in the scene.
     * Default: non-air blocks with a MODEL render shape.
     */
    protected boolean shouldRenderBlock(BlockPos worldPos, BlockState blockState) {
        return !blockState.isAir() && blockState.getRenderShape() == RenderShape.MODEL;
    }

    protected void renderBlock(PoseStack matrices, BlockPos worldPos, BlockState blockState,
                               int relX, int relY, int relZ) {
        if (coordinateMode == CoordinateMode.ABSOLUTE) {
            matrices.translate(worldPos.getX(), worldPos.getY(), worldPos.getZ());
        } else {
            matrices.translate(relX, relY, relZ);
        }
    }
}
