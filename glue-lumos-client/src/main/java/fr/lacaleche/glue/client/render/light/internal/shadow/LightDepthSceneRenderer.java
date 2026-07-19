package fr.lacaleche.glue.client.render.light.internal.shadow;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.lacaleche.glue.client.render.light.internal.scene.GlassSceneRenderer;
import fr.lacaleche.glue.client.render.scene.AbstractSceneRenderer;
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
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders scene depth from a light's point of view into an FBO &mdash; a shadow
 * map.
 *
 * <p>Geometry is placed in <strong>light-relative</strong> world coordinates
 * (block world pos minus the <em>light</em> position), with the light view matrix
 * looking out from the origin. That makes the map depend only on the light and the
 * blocks around it &mdash; not on the camera &mdash; which is what lets
 * {@link ShadowBaker} cache it across frames instead of re-rendering every frame.
 * It also keeps the coordinates small, so depth precision does not decay far from
 * the world origin. The deferred pass reconstructs a camera-relative position and
 * converts by subtracting the (camera-relative) light position.</p>
 *
 * <p>Overrides {@link AbstractSceneRenderer#buildModelViewMatrix} to drop the GUI
 * {@code -0.5} centering offset and scale: the modelview is exactly the light view
 * matrix, and each block is translated to its true light-relative position.</p>
 */
@Environment(EnvType.CLIENT)
public final class LightDepthSceneRenderer extends AbstractSceneRenderer {

    /** A block this close to the light is drawn into every face -- too near to cull safely. */
    static final double NEAR_LIGHT = 2.0;

    private Matrix4f lightView = new Matrix4f();
    private Matrix4f lightProj = new Matrix4f();
    private double lightX, lightY, lightZ;
    private BlockPos center = BlockPos.ZERO;
    private float range = 16f;
    private int faceAxis = -1;
    private float coneCosLimit = -1f;
    private final Vector3f coneDir = new Vector3f(0, -1, 0);
    private Casters suppliedCasters;

    /** Snapshot of the opaque-only depth, taken before the tint pass draws glass on top. */
    private int opaqueDepthTex = 0;
    private int copyReadFbo = 0;
    private int copyDrawFbo = 0;
    private int copySize = 0;
    private boolean hadTranslucents = false;

    /**
     * @param center     centre of the block region to rasterise
     * @param range      light range; casters beyond it are culled
     * @param faceAxis   cube face 0..5 to cull against, or -1 for a spot
     * @param coneDir    spot direction (ignored when {@code faceAxis >= 0})
     * @param coneCosLimit cosine of the spot's culling half-angle, or -1 to disable
     */
    public void configure(Matrix4f view, Matrix4f proj,
                          double lightX, double lightY, double lightZ,
                          BlockPos center, float range,
                          int faceAxis, Vector3f coneDir, float coneCosLimit) {
        this.lightView = view;
        this.lightProj = proj;
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightZ = lightZ;
        this.center = center;
        this.range = range;
        this.faceAxis = faceAxis;
        this.coneDir.set(coneDir);
        this.coneCosLimit = coneCosLimit;
        this.suppliedCasters = null;
    }

    void useCasters(Casters casters) {
        this.suppliedCasters = casters;
    }

    public LightDepthSceneRenderer() {
        // The colour attachment holds TRANSMITTANCE, so it starts fully transparent to
        // light -- white -- and translucent casters multiply themselves into it.
        this.clearColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    }

    /**
     * GL depth texture id of the last render, or -1. After a bake with translucent
     * casters this holds opaque <strong>plus</strong> glass &mdash; the with-translucents
     * map the tint compare samples ({@code shadowtex0} in shaderpack terms).
     */
    public int getDepthTextureId() {
        return framebuffer == null ? -1 : FramebufferHelper.getDepthTextureId(framebuffer);
    }

    /**
     * GL depth texture holding <strong>opaque casters only</strong> &mdash; what the PCSS
     * shadow test must sample. When the last bake had no translucent casters the live
     * depth buffer already IS opaque-only, so no copy was made and this returns it.
     */
    public int getOpaqueDepthTextureId() {
        return hadTranslucents ? opaqueDepthTex : getDepthTextureId();
    }

    /** Whether the last bake rasterised any translucent casters (so tint maps exist). */
    public boolean hasTranslucentCasters() {
        return hadTranslucents;
    }

    /** GL colour texture id holding per-texel light transmittance (glass tint), or -1. */
    public int getTintTextureId() {
        return framebuffer == null ? -1 : FramebufferHelper.getColorTextureId(framebuffer);
    }

    /** GL framebuffer name of this map, so the transmittance attachment can be blurred in place. */
    public int getFramebufferId() {
        return framebuffer == null ? -1 : FramebufferHelper.getFramebufferId(framebuffer);
    }

    @Override
    protected Matrix4f createProjectionMatrix(int width, int height) {
        return lightProj;
    }

    @Override
    protected Matrix4f buildViewMatrix() {
        return lightView;
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

    /**
     * Two passes over the same casters, <b>opaque first</b>, producing the two depth maps
     * a coloured shadow needs (shaderpack convention: {@code shadowtex1} = opaque only,
     * {@code shadowtex0} = opaque + translucent).
     *
     * <ol>
     *   <li><b>Opaque</b> blocks rasterise into depth (no colour). That depth is snapshot
     *       into {@link #getOpaqueDepthTextureId()} &mdash; the map the PCSS shadow test
     *       samples, in which glass must never appear.</li>
     *   <li><b>Translucent colour</b>: every pane multiplies its transmittance into the
     *       colour attachment &mdash; light through red then blue projects the merged
     *       violet, the way transmittance actually composes. Depth-tested against the
     *       opaque depth (a pane behind a wall tints nothing) but never writing it.
     *       Multiplication commutes, so this pass needs no caster sorting.</li>
     *   <li><b>Translucent depth</b>: the same casters again, depth only. The nearest
     *       pane joins the framebuffer's depth ({@link #getDepthTextureId()}), which
     *       becomes the with-translucents map the deferred pass compares receivers
     *       against &mdash; full depth precision, no distances packed into 8-bit alpha.</li>
     * </ol>
     *
     * <p>All passes are driven through Glue pipelines rather than the block's own render
     * type, because the write masks, depth test and multiplicative blend are pipeline state.</p>
     */
    @Override
    protected void renderScene(Minecraft client, PoseStack matrices) {
        if (client.level == null || framebuffer == null) return;

        Casters candidates = suppliedCasters != null
                ? suppliedCasters
                : collectCasters(client, center, lightX, lightY, lightZ, range);
        List<BlockPos> opaque = filterCasters(candidates.opaque());
        List<BlockPos> translucent = filterCasters(candidates.translucent());
        hadTranslucents = !translucent.isEmpty();

        drawCasters(client, matrices, opaque, ShadowPipelines.depth());

        if (hadTranslucents) {
            // Snapshot the opaque-only depth BEFORE glass writes into the live buffer.
            copyOpaqueDepth();

            drawCasters(client, matrices, translucent, ShadowPipelines.tintColor());
            drawCasters(client, matrices, translucent, ShadowPipelines.tintDepth());
        }
    }

    /**
     * Blit the framebuffer's depth (opaque-only at this point in the bake) into a private
     * depth texture, so the tint pass can then write glass into the live buffer without
     * corrupting the map the shadow test samples. Raw GL: MC's encoder has a depth
     * <em>clear</em> but no depth <em>copy</em>, and {@code glCopyImageSubData} is GL4.3
     * (no Mac). Read/draw FBOs are scratch wrappers around the two textures.
     */
    private void copyOpaqueDepth() {
        int size = framebuffer.width;   // maps are square
        int liveDepth = FramebufferHelper.getDepthTextureId(framebuffer);
        if (liveDepth <= 0) return;

        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int prevDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // MC's GlStateManager caches texture bindings; leave the unit as we found it.
        int prevTex = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);

        if (copySize != size || opaqueDepthTex == 0) {
            releaseCopy();
            copySize = size;

            opaqueDepthTex = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, opaqueDepthTex);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL14.GL_DEPTH_COMPONENT24, size, size, 0,
                    GL11.GL_DEPTH_COMPONENT, GL11.GL_UNSIGNED_INT, (java.nio.ByteBuffer) null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);

            copyReadFbo = GL30.glGenFramebuffers();
            copyDrawFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyDrawFbo);
            GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                    GL11.GL_TEXTURE_2D, opaqueDepthTex, 0);
            // Depth-only FBO: no colour buffers to draw to or read from.
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
        }

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, copyReadFbo);
        GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL11.GL_TEXTURE_2D, liveDepth, 0);
        GL11.glReadBuffer(GL11.GL_NONE);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, copyDrawFbo);
        GL30.glBlitFramebuffer(0, 0, size, size, 0, 0, size, size,
                GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevDraw);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTex);
    }

    private void releaseCopy() {
        if (copyReadFbo != 0) {
            GL30.glDeleteFramebuffers(copyReadFbo);
            copyReadFbo = 0;
        }
        if (copyDrawFbo != 0) {
            GL30.glDeleteFramebuffers(copyDrawFbo);
            copyDrawFbo = 0;
        }
        if (opaqueDepthTex != 0) {
            GL11.glDeleteTextures(opaqueDepthTex);
            opaqueDepthTex = 0;
        }
        copySize = 0;
    }

    @Override
    public void cleanup() {
        releaseCopy();
        super.cleanup();
    }

    private List<BlockPos> filterCasters(List<BlockPos> candidates) {
        List<BlockPos> result = new ArrayList<>(candidates.size());
        for (BlockPos pos : candidates) {
            if (!culled(pos)) result.add(pos);
        }
        return result;
    }

    static Casters collectCasters(Minecraft client, BlockPos center,
                                  double lightX, double lightY, double lightZ, float range) {
        List<BlockPos> opaque = new ArrayList<>();
        List<BlockPos> translucent = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double reach = range + 1.0;
        double reachSquared = reach * reach;
        int radius = (int) Math.ceil(reach);   // the box must contain the reach sphere
        int cameraChunkX = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().x)) >> 4;
        int cameraChunkZ = ((int) Math.floor(client.gameRenderer.getMainCamera().getPosition().z)) >> 4;
        int loadedRadius = client.options.getEffectiveRenderDistance() + 2;
        long minX = (long) center.getX() - radius;
        long maxX = (long) center.getX() + radius;
        long minY = (long) center.getY() - radius;
        long maxY = (long) center.getY() + radius;
        long minZ = (long) center.getZ() - radius;
        long maxZ = (long) center.getZ() + radius;
        int minChunkX = Math.max((int) Math.floorDiv(minX, 16L), cameraChunkX - loadedRadius);
        int maxChunkX = Math.min((int) Math.floorDiv(maxX, 16L), cameraChunkX + loadedRadius);
        int minChunkZ = Math.max((int) Math.floorDiv(minZ, 16L), cameraChunkZ - loadedRadius);
        int maxChunkZ = Math.min((int) Math.floorDiv(maxZ, 16L), cameraChunkZ + loadedRadius);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            int originX = chunkX << 4;
            int localMinX = (int) Math.max(0L, minX - originX);
            int localMaxX = (int) Math.min(15L, maxX - originX);
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                int originZ = chunkZ << 4;
                int localMinZ = (int) Math.max(0L, minZ - originZ);
                int localMaxZ = (int) Math.min(15L, maxZ - originZ);
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
                        for (int localX = localMinX; localX <= localMaxX; localX++) {
                            int worldX = originX + localX;
                            for (int localZ = localMinZ; localZ <= localMaxZ; localZ++) {
                                int worldZ = originZ + localZ;
                                pos.set(worldX, sectionY + localY, worldZ);
                                double dx = worldX + 0.5 - lightX;
                                double dy = sectionY + localY + 0.5 - lightY;
                                double dz = worldZ + 0.5 - lightZ;
                                if (dx * dx + dy * dy + dz * dz > reachSquared) continue;

                                BlockState state = section.getBlockState(localX, localY, localZ);
                                if (state.isAir() || state.getRenderShape() != RenderShape.MODEL) continue;
                                if (tintsLight(state)) {
                                    translucent.add(pos.immutable());
                                } else {
                                    opaque.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return new Casters(opaque, translucent);
    }

    record Casters(List<BlockPos> opaque, List<BlockPos> translucent) {
    }

    /**
     * Whether a caster tints the light passing through it rather than blocking it outright.
     *
     * <p>Broader than {@link GlassSceneRenderer#isGlass}, and deliberately so: that predicate asks which
     * blocks earn a glass BRDF, this one asks which let light through at all. A slime or honey block is
     * matte &mdash; no specular &mdash; yet still tints a shadow. The translucent layer answers this
     * correctly except for plain {@code GLASS} and {@code GLASS_PANE}, which vanilla maps to
     * {@code CUTOUT}; without the union they would cast solid shadows.</p>
     */
    private static boolean tintsLight(BlockState state) {
        return ItemBlockRenderTypes.getChunkRenderType(state) == ChunkSectionLayer.TRANSLUCENT
                || GlassSceneRenderer.isGlass(state);
    }

    private void drawCasters(Minecraft client, PoseStack matrices, List<BlockPos> casters, RenderType type) {
        if (casters.isEmpty()) return;

        BlockRenderDispatcher blockRenderer = client.getBlockRenderer();
        MultiBufferSource.BufferSource bufferSource = client.renderBuffers().bufferSource();
        // Force every block through our pipeline instead of its own render type.
        MultiBufferSource redirect = ignored -> bufferSource.getBuffer(type);

        for (BlockPos pos : casters) {
            matrices.pushPose();
            matrices.translate(
                    (float) (pos.getX() - lightX),
                    (float) (pos.getY() - lightY),
                    (float) (pos.getZ() - lightZ));
            blockRenderer.renderSingleBlock(client.level.getBlockState(pos), matrices, redirect,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            matrices.popPose();
        }

        bufferSource.endBatch(type);
    }

    /**
     * Reject casters that cannot possibly appear in this map. Without this a point
     * light would rasterise its whole block region six times over -- once per face --
     * which is what makes six-face baking affordable at all.
     */
    private boolean culled(BlockPos pos) {
        double dx = pos.getX() + 0.5 - lightX;
        double dy = pos.getY() + 0.5 - lightY;
        double dz = pos.getZ() + 0.5 - lightZ;

        // Out of reach. The +1 covers the block's own half-diagonal.
        double reach = range + 1.0;
        double d2 = dx * dx + dy * dy + dz * dz;
        if (d2 > reach * reach) return true;

        // Close to the light a block can straddle several faces (and the cone axis),
        // and the cheap tests below stop being conservative. Just keep it.
        if (d2 < NEAR_LIGHT * NEAR_LIGHT) return false;

        if (faceAxis >= 0) {
            // Keep the block if any of its corners falls in this face's 90-degree
            // pyramid. Testing corners rather than the centre stops blocks that
            // straddle a face seam from being dropped by both faces.
            for (int i = 0; i < 8; i++) {
                double cx = dx + ((i & 1) == 0 ? -0.5 : 0.5);
                double cy = dy + ((i & 2) == 0 ? -0.5 : 0.5);
                double cz = dz + ((i & 4) == 0 ? -0.5 : 0.5);
                if (dominantFace(cx, cy, cz) == faceAxis) return false;
            }
            return true;
        }

        if (coneCosLimit > -1f) {
            double len = Math.sqrt(d2);
            double cosA = (dx * coneDir.x + dy * coneDir.y + dz * coneDir.z) / len;
            // Angular half-size of a block at this distance, as a cosine slack.
            double slack = 0.9 / len;
            if (cosA < coneCosLimit - slack) return true;
        }
        return false;
    }

    /** Cube face (+X -X +Y -Y +Z -Z) a direction points at. Matches the shader's cubeFace(). */
    static int dominantFace(double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        if (ax >= ay && ax >= az) return x > 0 ? 0 : 1;
        if (ay >= az) return y > 0 ? 2 : 3;
        return z > 0 ? 4 : 5;
    }
}
