package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Drives the vanilla material capture into the {@link GBufferTargets} MRT.
 *
 * <p>Geometry is drawn once, by vanilla, but redirected into our multi-attachment framebuffer so
 * a single draw fills colour + material + depth together. The redirect is armed at
 * {@code GlRenderPass.setPipeline}, not by the draw method: Iris's {@code batchedentityrendering}
 * replaces the world-entity buffer source even with no shaderpack, so world entities never reach
 * vanilla's {@code CompositeRenderType.draw}. Every draw -- vanilla or Iris-batched -- still sets
 * its pipeline; only draws that own their pixels (see {@link #ownsItsPixels}) and use a patched
 * vanilla terrain, entity or particle shader are redirected.
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
    private static final ResourceLocation WATER_REDUCED_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/water_gbuffer_reduced");
    private static final ResourceLocation METAL_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/metal_gbuffer");
    private static final ResourceLocation TERRAIN_RERENDER_SHADER =
            ResourceLocation.fromNamespaceAndPath("glue", "internal/light/terrain_gbuffer");
    private static final GBufferTargets TARGETS = new GBufferTargets();

    private static boolean frameReady;
    private static boolean inWorldPhase;
    // True when this frame's G-buffer only holds the self-contained captures (Iris shaderpack
    // frame) -- base terrain/entities/particles are uncapturable, not missing.
    private static boolean reducedCapture;
    // Glass and water are captured by a post-world re-render, so each is armed explicitly around its
    // draw rather than by the world phase (which is already closed by then).
    private static boolean glassCaptureActive;
    private static boolean waterCaptureActive;
    private static boolean metalCaptureActive;
    private static boolean terrainRecaptureActive;
    private static boolean entityRecaptureActive;

    // Entity shadow maps re-render entities from a light's POV, post-world. Vanilla entity draws only
    // honour the framebuffer bound at the trySetup seam (not RenderSystem's output override), so the
    // shadow renderer arms this to redirect its entity draws into the shadow depth FBO.
    private static boolean entityShadowCaptureActive;
    private static int entityShadowFbo;

    // FBO to bind for the draw currently being set up. Armed at setPipeline (where the pipeline
    // is known), consumed at trySetup RETURN -- the last point before the draw, after Iris'
    // own framebuffer management, so our bind is the one that sticks.
    private static int pendingFbo;

    // True while the immediate draw in flight is the block-atlas particle sheet (break/run
    // particles). Its opaque block texels deserve capture, but it shares the blended
    // TRANSLUCENT_PARTICLE pipeline with the translucent particle atlas, so pipeline identity at
    // the arm seam cannot tell them apart -- the RenderType draw wrapping that seam flags it.
    private static boolean terrainParticleDraw;

    private GBufferCapture() {
    }

    /** Ensures + clears the G-buffer and opens the world phase. Call at world-render start. */
    public static void beginFrame() {
        frameReady = false;
        inWorldPhase = false;
        reducedCapture = false;
        if (!TerrainMaterialBuffer.isActive()) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        // Under an Iris shaderpack the scene's depth lives in Iris's render targets, not the main
        // target; the captures depth-test against the borrowed attachment, so borrow Iris's.
        boolean irisPack = RenderCompat.isIrisShaderEnabled();
        int depthOverride = 0;
        if (irisPack) {
            depthOverride = RenderCompat.getIrisMainDepthGlId();
            if (depthOverride <= 0) return;
        }
        if (TARGETS.ensure(main, depthOverride)) {
            TARGETS.clearMaterialAttachments();
            frameReady = true;
            reducedCapture = irisPack;
            // With a pack active the world phase never opens: Iris feeds the vanilla
            // RenderPipeline objects through setPipeline while overriding the actual programs, so
            // the entity/particle gates would match draws whose fragment stage carries none of our
            // outputs -- and the redirect would tear those draws out of the pack's frame. Only the
            // explicitly-armed self-contained captures may use the FBO on a pack frame.
            inWorldPhase = !irisPack;
        }
    }

    /** Closes the world phase so post-world entity-format draws (GUI, hand-held items) are not
     *  captured. Call at post-world-render. */
    public static void endWorldPhase() {
        if (inWorldPhase && frameReady) TARGETS.restoreMaterialBlend();
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

    /**
     * Opens the terrain re-capture: the reduced-frame stand-in for the native terrain capture
     * (which an Iris pack's program ownership blocks), restricting the shared FBO to its material
     * attachments and arming the redirect for the terrain re-render's draws. Returns false (drawing
     * nothing) if the target is not ready. Pair every {@code true} return with
     * {@link #endTerrainRecapture()}.
     */
    public static boolean beginTerrainRecapture() {
        if (!frameReady) return false;
        TARGETS.beginGlassPass();
        terrainRecaptureActive = true;
        return true;
    }

    /** Closes the terrain re-capture and restores the full draw-buffer set. */
    public static void endTerrainRecapture() {
        if (!terrainRecaptureActive) return;
        terrainRecaptureActive = false;
        TARGETS.endGlassPass();
    }

    /**
     * Opens the entity re-capture: the reduced-frame stand-in for the world-phase entity capture.
     * Entities draw through their ordinary vanilla pipelines (whose patched shaders write the
     * material outputs), so unlike the Glue-pipeline captures this cannot carry a depth bias or a
     * depth mask on the pipeline &mdash; the decal discipline is applied at the GL level instead:
     * a polygon offset for the whole capture (so the LEQUAL re-test against the scene's own entity
     * depth passes without z-fight dither) and a forced depth-mask-off per redirected draw (see
     * {@link #consumePendingRedirect()}), protecting the borrowed scene depth. Both go through
     * {@link GlStateManager} so its state cache stays truthful. Returns false (drawing nothing) if
     * the target is not ready. Pair every {@code true} return with {@link #endEntityRecapture()}.
     */
    public static boolean beginEntityRecapture() {
        if (!frameReady) return false;
        TARGETS.beginGlassPass();
        GlStateManager._polygonOffset(-1.0f, -10.0f);
        GlStateManager._enablePolygonOffset();
        entityRecaptureActive = true;
        return true;
    }

    /** Closes the entity re-capture and restores the full draw-buffer set. */
    public static void endEntityRecapture() {
        if (!entityRecaptureActive) return;
        entityRecaptureActive = false;
        GlStateManager._disablePolygonOffset();
        TARGETS.endGlassPass();
    }

    /** Called at {@code setPipeline}: record whether the draw about to run should be redirected. */
    public static void armForPipeline(RenderPipeline pipeline) {
        pendingFbo = redirectFboFor(pipeline);
    }

    /** Called at {@code CompositeRenderType.draw} HEAD, before its {@code setPipeline}. */
    public static void beginRenderTypeDraw(RenderType type) {
        terrainParticleDraw = type == ParticleRenderType.TERRAIN_SHEET.renderType();
    }

    /** Called at {@code CompositeRenderType.draw} RETURN. */
    public static void endRenderTypeDraw() {
        terrainParticleDraw = false;
    }

    /** Called at {@code trySetup} RETURN: the FBO to bind for this draw (0 = leave alone). */
    public static int consumePendingRedirect() {
        int fbo = pendingFbo;
        pendingFbo = 0;
        // The per-draw seam for the material attachments' blend state as well as for the bind: this
        // runs after Blaze3D has applied the pipeline's blend, which is the only point at which the
        // suppression survives to the draw. Skipped for the shadow FBO, which has no attachment 1..3.
        if (fbo != 0 && fbo == TARGETS.framebufferId()) {
            TARGETS.suppressMaterialBlend();
            // The entity re-capture's FBO borrows the scene's live depth, and the vanilla entity
            // pipelines it draws through all declare depth writes -- force the mask off after each
            // pipeline application so the re-render can never corrupt the scene depth. Through
            // GlStateManager, so the next pipeline's own depth-mask request re-applies for real.
            if (entityRecaptureActive) GlStateManager._depthMask(false);
        }
        return fbo;
    }

    /**
     * True when a draw establishes the visible surface at the pixels it covers: it writes colour,
     * writes depth, and depth-tests with the ordinary LEQUAL function; and its blend, if it has
     * one, is alpha compositing -- whose {@code ONE_MINUS_SRC_ALPHA} destination factor wipes out
     * whatever was behind once the source texel is opaque.
     *
     * <p>Carrying a blend function does NOT make geometry translucent, and rejecting on it is what
     * left the player unlit: vanilla's {@code ENTITY_TRANSLUCENT} (the player, piglins, allays,
     * wardens, armour stands, ...) is {@code ALPHA_CUTOUT} geometry that keeps {@code writeDepth}
     * and LEQUAL, and merely carries a blend for the rare part-transparent skin. Because it writes
     * correct depth, the world-space ownership test downstream already works on it.
     *
     * <p>What this still keeps out is geometry that does NOT own its pixels: anything that does not
     * write depth ({@code ENTITY_TRANSLUCENT_EMISSIVE}, {@code EYES}, {@code ENTITY_NO_OUTLINE}),
     * a decal's {@code EQUAL} re-test ({@code ARMOR_DECAL_CUTOUT_NO_CULL}), and -- the reason the
     * blend is inspected rather than ignored -- an additive blend, which only ever adds light on
     * top of a surface somebody else owns ({@code ENERGY_SWIRL} writes depth with LEQUAL, so
     * nothing else here would reject it).
     */
    private static boolean ownsItsPixels(RenderPipeline pipeline) {
        if (!pipeline.isWriteColor()
                || !pipeline.isWriteDepth()
                || pipeline.getDepthTestFunction() != DepthTestFunction.LEQUAL_DEPTH_TEST) {
            return false;
        }
        Optional<BlendFunction> blend = pipeline.getBlendFunction();
        return blend.isEmpty() || blend.get().destColor() == DestFactor.ONE_MINUS_SRC_ALPHA;
    }

    /**
     * The framebuffer to bind for a draw about to use {@code pipeline}, or 0 to leave it alone.
     * Non-zero only for pixel-owning terrain, entity or particle geometry during the world phase.
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
                && (WATER_SHADER.equals(pipeline.getFragmentShader())
                        || WATER_REDUCED_SHADER.equals(pipeline.getFragmentShader()))) {
            return TARGETS.framebufferId();
        }
        // Metal: a post-world re-render of nearby curated metal blocks, armed explicitly like glass.
        if (metalCaptureActive && frameReady
                && METAL_SHADER.equals(pipeline.getFragmentShader())) {
            return TARGETS.framebufferId();
        }
        // Terrain re-capture: the reduced-frame re-render of nearby base terrain, armed explicitly
        // like glass. Distinct from the world-phase terrain gate below, which keys on the vanilla
        // chunk pipelines this re-render never uses.
        if (terrainRecaptureActive && frameReady
                && TERRAIN_RERENDER_SHADER.equals(pipeline.getFragmentShader())) {
            return TARGETS.framebufferId();
        }
        // Entity re-capture: the reduced-frame re-render of entities near lights, armed explicitly.
        // Same pipeline gate as the world-phase entity capture below -- the draws come through the
        // ordinary vanilla entity pipelines, whose patched shaders carry the material outputs.
        if (entityRecaptureActive && frameReady
                && ownsItsPixels(pipeline) && isEntityMaterialPipeline(pipeline)) {
            return TARGETS.framebufferId();
        }
        if (!frameReady || !inWorldPhase) return 0;
        if (!ownsItsPixels(pipeline)) return 0;
        boolean entity = isEntityMaterialPipeline(pipeline);
        // Particles keep the stricter unblended gate that ownsItsPixels no longer applies. A
        // translucent billboard (smoke, spell, glow) has no single albedo or normal to capture, so
        // the deferred cap is the right answer for it; and TRANSLUCENT_PARTICLE / WEATHER_DEPTH_WRITE
        // do write depth with LEQUAL, so ownsItsPixels alone would pull the whole sheet in. The one
        // blended exception is the block-atlas TERRAIN_SHEET (break/run particles): opaque block
        // texels behind vanilla's 0.1-alpha discard, depth-written -- flagged per draw by its
        // RenderType because it shares this pipeline with the translucent particle atlas.
        boolean particle = CoreShaderMaterialPatch.isParticleReady()
                && (pipeline.getBlendFunction().isEmpty() || terrainParticleDraw)
                && PARTICLE_SHADER.equals(pipeline.getVertexShader())
                && PARTICLE_SHADER.equals(pipeline.getFragmentShader())
                && pipeline.getVertexFormat() == DefaultVertexFormat.PARTICLE;
        // Terrain is matched by PIPELINE IDENTITY, not by shader, because core/terrain is shared:
        // RenderPipelines.WIREFRAME is built from the same TERRAIN_SNIPPET and inherits no-blend +
        // writeDepth + LEQUAL, so a shader-keyed gate would redirect it too and stamp a debug
        // wireframe as solid terrain. The public pipeline API (PipelineCodecs' `terrain` category)
        // hands out TERRAIN_SNIPPET as well, so consumer pipelines would be caught the same way.
        // These three ARE the opaque chunk layers, though not exclusively: RenderType.solid() and
        // friends hand the same objects to non-chunk geometry, which then captures as terrain --
        // correct, since it is opaque BLOCK-format geometry carrying a real normal.
        boolean terrain = CoreShaderMaterialPatch.isTerrainReady()
                && (pipeline == RenderPipelines.SOLID
                        || pipeline == RenderPipelines.CUTOUT_MIPPED
                        || pipeline == RenderPipelines.CUTOUT);
        return (entity || particle || terrain) ? TARGETS.framebufferId() : 0;
    }

    /**
     * A pixel-owning vanilla entity draw whose patched shader writes the material outputs.
     * isWriteAlpha is load-bearing, not a proxy for opacity: glColorMask is not per-attachment,
     * so a draw that masks alpha would drop the packed normal in attachment 1's alpha and the
     * low byte of the owning depth in attachment 2's.
     */
    private static boolean isEntityMaterialPipeline(RenderPipeline pipeline) {
        return CoreShaderMaterialPatch.isEntityReady()
                && pipeline.isWriteAlpha()
                && ENTITY_SHADER.equals(pipeline.getVertexShader())
                && ENTITY_SHADER.equals(pipeline.getFragmentShader())
                && pipeline.getVertexFormat() == DefaultVertexFormat.NEW_ENTITY;
    }

    /** True when this frame's G-buffer holds only the self-contained captures (Iris shaderpack
     *  frame): unclaimed pixels are uncapturable base surfaces, not capture failures. */
    public static boolean isReducedCapture() {
        return frameReady && reducedCapture;
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
