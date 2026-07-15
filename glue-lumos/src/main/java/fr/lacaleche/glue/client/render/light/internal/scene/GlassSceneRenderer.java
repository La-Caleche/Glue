package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Re-rasterises nearby translucent blocks from the <strong>camera's</strong> point of view
 * into the shared material G-buffer, stamping them with the {@code GLASS} material id.
 *
 * <p>The deferred light pass shades whatever surface the scene depth buffer holds, and
 * "is this surface glass?" cannot be answered from depth alone. It is answered here by a
 * real per-pixel material id: the glass is re-drawn with the <em>exact frame matrices</em>
 * ({@link fr.lacaleche.glue.client.utils.FrameMatrices}), depth-tested (read-only) against
 * the main depth so only the frontmost pane survives, and its albedo + opacity and
 * {@code id=4} + owner-depth are written into the same G-buffer attachments that terrain,
 * entities and particles fill. The draws are redirected into that buffer at the
 * command-encoder seam; only attachments 1 and 2 are written, because vanilla's own
 * translucent pass already blended the pane colour into the main target.</p>
 *
 * <p>Re-rendered once per frame (camera-dependent, so not cacheable like shadow maps),
 * but the <em>block list</em> it draws is cached per light by {@code LightRenderer}
 * and invalidated on block changes, so the per-frame cost is rasterising a handful
 * of blocks, not scanning the world.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlassSceneRenderer extends AbstractSceneRenderer {

    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<BlockPos> blocks = List.of();

    /**
     * @param view   the frame's real view matrix (camera-relative, includes bobbing)
     * @param proj   the frame's real projection matrix
     * @param blocks translucent blocks to rasterise, world coordinates
     */
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
        // Camera-relative like the level itself: no GUI centering offset, no scale.
        PoseStack matrices = new PoseStack();
        matrices.mulPose(viewMatrix);
        return matrices;
    }

    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null || blocks.isEmpty()) return;
        // Route these draws into the shared G-buffer's material attachments. Draws nothing if the
        // buffer is not ready this frame -- rendering unredirected would paint opaque panes over
        // the scene.
        if (!GBufferCapture.beginGlassCapture()) return;
        try {
            RenderType type = ShadowPipelines.glassGBuffer();
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
            GBufferCapture.endGlassCapture();
        }
    }

    /**
     * Translucent blocks within a light's reach. This is the scan that must NOT run
     * per frame &mdash; the caller caches the result per light and invalidates it on
     * block changes, exactly like the shadow maps.
     */
    public static List<BlockPos> collectTranslucents(Minecraft client,
                                                     double lx, double ly, double lz, float range) {
        if (client.level == null) return List.of();

        int radius = Math.max(2, (int) Math.ceil(range));
        BlockPos center = BlockPos.containing(lx, ly, lz);
        double reach = range + 1.0;   // +1 covers the block's own half-diagonal
        List<BlockPos> result = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int cameraChunkX = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().x)) >> 4;
        int cameraChunkZ = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().z)) >> 4;
        int loadedRadius = client.options.getEffectiveRenderDistance() + 2;
        int minChunkX = Math.max((int) Math.floorDiv((long) center.getX() - radius, 16L),
                cameraChunkX - loadedRadius);
        int maxChunkX = Math.min((int) Math.floorDiv((long) center.getX() + radius, 16L),
                cameraChunkX + loadedRadius);
        int minChunkZ = Math.max((int) Math.floorDiv((long) center.getZ() - radius, 16L),
                cameraChunkZ - loadedRadius);
        int maxChunkZ = Math.min((int) Math.floorDiv((long) center.getZ() + radius, 16L),
                cameraChunkZ + loadedRadius);
        long minY = (long) center.getY() - radius;
        long maxY = (long) center.getY() + radius;

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = client.level.getChunkSource().getChunk(
                        chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                LevelChunkSection[] sections = chunk.getSections();
                for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
                    LevelChunkSection section = sections[sectionIndex];
                    if (section.hasOnlyAir()) continue;
                    int sectionY = chunk.getSectionYFromSectionIndex(sectionIndex) << 4;
                    if (sectionY > maxY || sectionY + 15 < minY) continue;
                    int localMinY = (int) Math.max(0L, minY - sectionY);
                    int localMaxY = (int) Math.min(15L, maxY - sectionY);
                    for (int localY = localMinY; localY <= localMaxY; localY++) {
                        for (int localX = 0; localX < 16; localX++) {
                            for (int localZ = 0; localZ < 16; localZ++) {
                                int worldX = (chunkX << 4) + localX;
                                int worldZ = (chunkZ << 4) + localZ;
                                pos.set(worldX, sectionY + localY, worldZ);
                                double dx = worldX + 0.5 - lx;
                                double dy = sectionY + localY + 0.5 - ly;
                                double dz = worldZ + 0.5 - lz;
                                if (dx * dx + dy * dy + dz * dz > reach * reach) continue;
                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                                if (ItemBlockRenderTypes.getChunkRenderType(state)
                                        == ChunkSectionLayer.TRANSLUCENT) {
                                    result.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }
}
