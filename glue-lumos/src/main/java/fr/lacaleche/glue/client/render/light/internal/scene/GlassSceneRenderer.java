package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
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
 * Renders nearby translucent blocks from the <strong>camera's</strong> point of view
 * into a private albedo + depth target &mdash; a glass G-buffer.
 *
 * <p>The deferred light pass shades whatever surface the scene depth buffer holds, and
 * everything it knows about that surface is reconstructed from depth. That is enough
 * for opaque receivers, but "is this surface glass?" cannot be answered from depth
 * alone, and answering it from the shadow map's tint texel breaks down on faces that
 * are edge-on to the light (they barely rasterise into the map, so the texel they
 * sample back belongs to some other pane). This buffer answers it geometrically
 * instead: re-rasterise the glass with the <em>exact frame matrices</em>
 * ({@link fr.lacaleche.glue.client.utils.FrameMatrices}), and a pixel whose scene
 * depth matches this buffer's depth is looking at glass &mdash; and this buffer's
 * colour is that pane's own albedo, which is what the glow should be tinted with.</p>
 *
 * <p>Rendered once per frame (camera-dependent, so not cacheable like shadow maps),
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

    public GlassSceneRenderer() {
        // Colour is albedo; depth is the mask. A cleared texel keeps depth 1.0, which
        // the deferred pass reads as "no glass here" -- the clear colour never matches
        // a real surface, so it needs no meaning of its own.
        this.clearColor = new float[]{0.0f, 0.0f, 0.0f, 1.0f};
    }

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

    /** GL depth texture id of the last render, or -1. */
    public int getDepthTextureId() {
        return framebuffer == null ? -1 : FramebufferHelper.getDepthTextureId(framebuffer);
    }

    /** GL colour texture id holding the nearest pane's albedo per pixel, or -1. */
    public int getColorTextureId() {
        return framebuffer == null ? -1 : FramebufferHelper.getColorTextureId(framebuffer);
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
        if (client.level == null || framebuffer == null || blocks.isEmpty()) return;

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
