package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Drives the dynamic (entity) material capture into the {@link GBufferTargets} MRT.
 *
 * <p>Geometry is drawn once, by vanilla, but redirected into our multi-attachment framebuffer so
 * a single draw fills colour + material + depth together. The redirect is armed by
 * <em>pipeline vertex format</em> at {@code GlRenderPass.setPipeline}, not by the draw method:
 * Iris's {@code batchedentityrendering} replaces the world-entity buffer source even with no
 * shaderpack, so world entities never reach vanilla's {@code CompositeRenderType.draw}. Every
 * draw -- vanilla or Iris-batched -- still sets its pipeline; only unblended, depth-writing draws
 * using the patched vanilla entity shader are redirected.
 *
 * <p>Gated to the world phase (between world-render start and post-world) so later entity-format
 * draws (GUI items, the outline pass) are not redirected, and to the same conditions as terrain
 * material (Lumos active, vanilla Fancy path) so Iris shaderpack frames are never touched. Our
 * FBO carries the main colour as attachment 0, so the ordinary scene is still drawn.
 */
@Environment(EnvType.CLIENT)
public final class GBufferCapture {

    private static final ResourceLocation ENTITY_SHADER =
            ResourceLocation.withDefaultNamespace("core/entity");
    private static final ResourceLocation PARTICLE_SHADER =
            ResourceLocation.withDefaultNamespace("core/particle");
    // The Lumos glass/water captures are Glue pipelines; their fragment shaders identify them uniquely.
    private static final ResourceLocation GLASS_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/glass_gbuffer");
    private static final ResourceLocation WATER_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/water_gbuffer");
    private static final ResourceLocation METAL_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/metal_gbuffer");
    private static final GBufferTargets TARGETS = new GBufferTargets();

    private static boolean frameReady;
    private static boolean inWorldPhase;
    // Glass and water are captured by a post-world re-render, so each is armed explicitly around its
    // draw rather than by the world phase (which is already closed by then).
    private static boolean glassCaptureActive;
    private static boolean waterCaptureActive;
    private static boolean metalCaptureActive;

    // Entity shadow maps re-render entities from a light's POV, post-world. Vanilla entity draws only
    // honour the framebuffer bound at the trySetup seam (not RenderSystem's output override), so the
    // shadow renderer arms this to redirect its entity draws into the shadow depth FBO.
    private static boolean entityShadowCaptureActive;
    private static int entityShadowFbo;

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

    /** True once the shared G-buffer is ensured and cleared for this frame. Glass capture, which
     *  runs post-world (outside the world phase), must check this before it draws: with no target
     *  ready its redirect would resolve to 0 and the pane re-render would corrupt the main scene. */
    public static boolean isReady() {
        return frameReady;
    }

    /**
     * Opens the glass capture: restricts the shared FBO to its material attachments (1,2) and arms
     * the redirect for glass draws. Returns false (drawing nothing) if the target is not ready.
     * Pair every {@code true} return with {@link #endGlassCapture()}.
     */
    public static boolean beginGlassCapture() {
        if (!frameReady) return false;
        TARGETS.beginGlassPass();
        glassCaptureActive = true;
        return true;
    }

    /**
     * Redirect entity draws into {@code fbo} (a light-POV shadow depth target) until
     * {@link #endEntityShadowCapture()}. This is the ONLY reliable way to contain a re-rendered
     * entity mid-frame: vanilla entity render types target the bound framebuffer at the trySetup
     * seam, ignoring both a raw pre-bind and RenderSystem's output-texture override.
     */
    public static void beginEntityShadowCapture(int fbo) {
        entityShadowFbo = fbo;
        entityShadowCaptureActive = fbo != 0;
    }

    public static void endEntityShadowCapture() {
        entityShadowCaptureActive = false;
        entityShadowFbo = 0;
    }

    /** Closes the glass capture and restores the full draw-buffer set. */
    public static void endGlassCapture() {
        if (!glassCaptureActive) return;
        glassCaptureActive = false;
        TARGETS.endGlassPass();
    }

    /**
     * Opens the water capture: like {@link #beginGlassCapture()} it restricts the shared FBO to its
     * material attachments (1,2,3) and arms the redirect for water draws. Water and glass share the
     * same material-only pass setup ({@code beginGlassPass}). Returns false (drawing nothing) if the
     * target is not ready. Pair every {@code true} return with {@link #endWaterCapture()}.
     */
    public static boolean beginWaterCapture() {
        if (!frameReady) return false;
        TARGETS.beginGlassPass();
        waterCaptureActive = true;
        return true;
    }

    /** Closes the water capture and restores the full draw-buffer set. */
    public static void endWaterCapture() {
        if (!waterCaptureActive) return;
        waterCaptureActive = false;
        TARGETS.endGlassPass();
    }

    /**
     * Opens the metal capture: like glass/water it restricts the shared FBO to its material
     * attachments (1,2,3) and arms the redirect for metal draws. Returns false (drawing nothing) if
     * the target is not ready. Pair every {@code true} return with {@link #endMetalCapture()}.
     */
    public static boolean beginMetalCapture() {
        if (!frameReady) return false;
        TARGETS.beginGlassPass();
        metalCaptureActive = true;
        return true;
    }

    /** Closes the metal capture and restores the full draw-buffer set. */
    public static void endMetalCapture() {
        if (!metalCaptureActive) return;
        metalCaptureActive = false;
        TARGETS.endGlassPass();
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
     * Non-zero only for opaque, depth-writing entity or particle geometry during the world phase.
     *
     * <p>The unblended + write-depth + LEQUAL gate is what makes this safe: it captures the opaque
     * entity and the opaque/lit particle sheets (which on the Fancy path both draw into the main
     * target) and rejects the translucent particle sheet, entity shadows, and outline passes.
     */
    private static int redirectFboFor(RenderPipeline pipeline) {
        if (pipeline == null) return 0;
        // Entity shadow: a post-world re-render of entities from a light's POV. Independent of the
        // material G-buffer -- redirect any entity-format draw into the armed shadow depth FBO. Only
        // the shadow renderer's own entity re-render happens while this is active, so a broad
        // vertex-format match is safe and catches every entity part (armour, held items, effects).
        if (entityShadowCaptureActive
                && pipeline.getVertexFormat() == DefaultVertexFormat.NEW_ENTITY) {
            return entityShadowFbo;
        }
        // Glass: a post-world re-render of nearby panes, armed explicitly (not by the world phase).
        // It writes only attachments 1/2 -- attachment 0 keeps the pane colour vanilla already
        // blended -- and never writes main depth (the pipeline masks it), so it is safe to redirect
        // this one Glue pipeline outside the entity/particle gate below.
        if (glassCaptureActive && frameReady
                && GLASS_SHADER.equals(pipeline.getFragmentShader())) {
            return TARGETS.framebufferId();
        }
        // Water: a post-world re-render of nearby fluid surfaces, armed explicitly like glass. Same
        // material-only redirect -- attachment 0 keeps the water colour vanilla already blended.
        if (waterCaptureActive && frameReady
                && WATER_SHADER.equals(pipeline.getFragmentShader())) {
            return TARGETS.framebufferId();
        }
        // Metal: a post-world re-render of nearby curated metal blocks, armed explicitly like glass.
        if (metalCaptureActive && frameReady
                && METAL_SHADER.equals(pipeline.getFragmentShader())) {
            return TARGETS.framebufferId();
        }
        if (!frameReady || !inWorldPhase) return 0;
        if (pipeline.getBlendFunction().isPresent()
                || !pipeline.isWriteColor()
                || !pipeline.isWriteDepth()
                || pipeline.getDepthTestFunction() != DepthTestFunction.LEQUAL_DEPTH_TEST) {
            return 0;
        }
        boolean entity = CoreShaderMaterialPatch.isEntityReady()
                && pipeline.isWriteAlpha()
                && ENTITY_SHADER.equals(pipeline.getVertexShader())
                && ENTITY_SHADER.equals(pipeline.getFragmentShader())
                && pipeline.getVertexFormat() == DefaultVertexFormat.NEW_ENTITY;
        boolean particle = CoreShaderMaterialPatch.isParticleReady()
                && PARTICLE_SHADER.equals(pipeline.getVertexShader())
                && PARTICLE_SHADER.equals(pipeline.getFragmentShader())
                && pipeline.getVertexFormat() == DefaultVertexFormat.PARTICLE;
        return (entity || particle) ? TARGETS.framebufferId() : 0;
    }

    /** Albedo (RGB) + packed normal (A), or -1 if not captured this frame. */
    public static int albedoNormalTextureId() {
        return frameReady ? TARGETS.albedoNormalTextureId() : -1;
    }

    /** Material-id attachment, or -1 if not captured this frame. */
    public static int materialIdTextureId() {
        return frameReady ? TARGETS.materialIdTextureId() : -1;
    }

    /** Material-properties attachment (roughness/metalness/F0), or -1 if not captured this frame. */
    public static int materialPropsTextureId() {
        return frameReady ? TARGETS.materialPropsTextureId() : -1;
    }

    public static void cleanup() {
        TARGETS.cleanup();
        frameReady = false;
        inWorldPhase = false;
    }
}
