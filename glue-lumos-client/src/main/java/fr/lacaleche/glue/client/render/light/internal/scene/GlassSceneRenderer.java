package fr.lacaleche.glue.client.render.light.internal.scene;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowPipelines;
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
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Set;

/**
 * Re-rasterises nearby glass blocks from the <strong>camera's</strong> point of view
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
 * terrain passes already drew the pane colour into the main target.</p>
 *
 * <p>Re-rendered once per frame (camera-dependent, so not cacheable like shadow maps),
 * but the <em>block list</em> it draws comes from {@link MaterialBlockScan}, which is
 * cached per light and invalidated on block changes, so the per-frame cost is
 * rasterising a handful of blocks, not scanning the world.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlassSceneRenderer extends AbstractSceneRenderer {

    /** Glass named block by block because no class check captures it cleanly: {@code GLASS} is a plain
     *  {@code TransparentBlock}, a subtree that also holds the copper grates, and {@code GLASS_PANE} an
     *  {@code IronBarsBlock}, shared with {@code IRON_BARS}. {@code TINTED_GLASS} joins them rather than
     *  earning a fourth {@code instanceof} for a single block. */
    private static final Set<Block> GLASS_BLOCKS =
            Set.of(Blocks.GLASS, Blocks.GLASS_PANE, Blocks.TINTED_GLASS);

    /**
     * Whether Lumos gives this block the glass BRDF. Vanilla carries no PBR data, so &mdash; like
     * {@link MetalSceneRenderer#isMetal} &mdash; this predicate is the deliberate data source; edit here
     * to add or remove glass.
     *
     * <p>The chunk render layer cannot stand in for it, in either direction: vanilla maps {@code GLASS}
     * to {@code CUTOUT} and {@code GLASS_PANE} to {@code CUTOUT_MIPPED}, so a {@code TRANSLUCENT} test
     * misses plain glass entirely, while that same layer also carries the matte {@code SLIME_BLOCK} and
     * {@code HONEY_BLOCK} and the {@code NETHER_PORTAL} effect block, none of which want a sharp
     * specular and a mirror reflection.</p>
     *
     * <p>The ~32 stained variants are exactly the {@link StainedGlassBlock} and
     * {@link StainedGlassPaneBlock} hierarchies, so they are matched by class instead of listed.
     * {@link IceBlock} covers {@code ICE} and {@code FROSTED_ICE} &mdash; smooth, translucent and
     * refractive enough for a glass BRDF to be a fair approximation &mdash; and notably not the opaque
     * {@code PACKED_ICE} or {@code BLUE_ICE}, which are not {@code IceBlock}s.</p>
     */
    public static boolean isGlass(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StainedGlassBlock
                || block instanceof StainedGlassPaneBlock
                || block instanceof IceBlock
                || GLASS_BLOCKS.contains(block);
    }

    private Matrix4f view = new Matrix4f();
    private Matrix4f proj = new Matrix4f();
    private double camX, camY, camZ;
    private List<BlockPos> blocks = List.of();

    /**
     * @param view   the frame's real view matrix (camera-relative, includes bobbing)
     * @param proj   the frame's real projection matrix
     * @param blocks glass blocks to rasterise, world coordinates
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
}
