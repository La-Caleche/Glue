package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;

/**
 * Drives the dynamic (entity) material capture into the {@link GBufferTargets} MRT.
 *
 * <p>Geometry is drawn once, by vanilla, but redirected into our multi-attachment framebuffer so
 * a single draw fills colour + material + depth together. The redirect is armed by
 * <em>pipeline vertex format</em> at {@code GlRenderPass.setPipeline}, not by the draw method:
 * Iris's {@code batchedentityrendering} replaces the world-entity buffer source even with no
 * shaderpack, so world entities never reach vanilla's {@code CompositeRenderType.draw}. Every
 * draw -- vanilla or Iris-batched -- still sets its pipeline, and every entity pipeline uses
 * {@code NEW_ENTITY}, so keying on that catches them all.
 *
 * <p>Gated to the world phase (between world-render start and post-world) so later entity-format
 * draws (GUI items, the outline pass) are not redirected, and to the same conditions as terrain
 * material (Lumos active, vanilla Fancy path) so Iris shaderpack frames are never touched. Our
 * FBO carries the main colour as attachment 0, so the ordinary scene is still drawn.
 */
@Environment(EnvType.CLIENT)
public final class GBufferCapture {

    private static final GBufferTargets TARGETS = new GBufferTargets();

    private static boolean frameReady;
    private static boolean inWorldPhase;

    // FBO to bind for the draw currently being set up. Armed at setPipeline (where the pipeline
    // is known), consumed at trySetup RETURN -- the last point before the draw, after Iris'
    // own framebuffer management, so our bind is the one that sticks.
    private static int pendingFbo;

    private GBufferCapture() {
    }

    /** Ensures + clears the G-buffer and opens the world phase. Call at world-render start. */
    public static void beginFrame() {
        frameReady = false;
        inWorldPhase = false;
        if (!TerrainMaterialBuffer.isActive()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (TARGETS.ensure(main)) {
            TARGETS.clearMaterialAttachments();
            frameReady = true;
            inWorldPhase = true;
        }
    }

    /** Closes the world phase so post-world entity-format draws (GUI, hand-held items) are not
     *  captured. Call at post-world-render. */
    public static void endWorldPhase() {
        inWorldPhase = false;
    }

    /** Called at {@code setPipeline}: record whether the draw about to run should be redirected. */
    public static void armForPipeline(RenderPipeline pipeline) {
        pendingFbo = redirectFboFor(pipeline);
    }

    /** Called at {@code trySetup} RETURN: the FBO to bind for this draw (0 = leave alone). */
    public static int consumePendingRedirect() {
        int fbo = pendingFbo;
        pendingFbo = 0;
        return fbo;
    }

    /**
     * The framebuffer to bind for a draw about to use {@code pipeline}, or 0 to leave it alone.
     * Non-zero only for opaque/cutout entity geometry during the world phase.
     */
    private static int redirectFboFor(RenderPipeline pipeline) {
        if (!frameReady || !inWorldPhase || pipeline == null) return 0;
        if (pipeline.getVertexFormat() != DefaultVertexFormat.NEW_ENTITY) return 0;
        return TARGETS.framebufferId();
    }

    /** Albedo (RGB) + packed normal (A), or -1 if not captured this frame. */
    public static int albedoNormalTextureId() {
        return frameReady ? TARGETS.albedoNormalTextureId() : -1;
    }

    /** Material-id attachment, or -1 if not captured this frame. */
    public static int materialIdTextureId() {
        return frameReady ? TARGETS.materialIdTextureId() : -1;
    }

    public static void cleanup() {
        TARGETS.cleanup();
        frameReady = false;
        inWorldPhase = false;
    }
}
