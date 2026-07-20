package fr.lacaleche.glue.mcsx.viewport;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.mcsx.client.mixin.GlCommandEncoderAccessor;
import org.lwjgl.opengl.GL33C;

/**
 * Re-synchronizes Blaze3D's GL state caches with the actual GL state after a block of raw GL
 * (Arc3D's flush, the dock present pass, external surface sources). Blaze3D skips redundant state
 * changes against several caches; raw GL that bypasses them leaves a cache believing state that is
 * no longer bound, and the next vanilla change that happens to match the stale belief is silently
 * skipped — a one-frame corruption of whatever draws next (historically: the HUD, on every frame
 * the dock UI repainted).
 *
 * <p>Each desync found gets a permanent entry here rather than a spot fix:
 * <ul>
 *   <li><b>Framebuffer bindings</b> — {@code _glBindFramebuffer} caches read/draw FBOs and skips
 *       matching binds; the cached values are readable, so the actual binding is forced back to
 *       what the cache believes.</li>
 *   <li><b>Shader program</b> — {@code GlCommandEncoder.lastProgram} skips {@code glUseProgram}
 *       and is never reset per pass; it is write-only from outside, so the cache is invalidated
 *       (nulled) to force a real re-bind on the next draw.</li>
 *   <li><b>Color mask</b> — cached in {@code GlStateManager}; Arc3D disables color writes for
 *       stencil-clip passes. The wrapper is set first (fixes the cache), then the raw call (fixes
 *       the actual state when the wrapper skipped).</li>
 *   <li><b>Face culling</b> — same pattern; the reset in {@code UIManager.render} only calls the
 *       cached wrapper, which is a no-op when the cache already believes culling is off.</li>
 *   <li><b>Samplers, blend func/equation, texture bindings</b> — the shared epilogue
 *       {@link #resetSamplersBlendTextures()}, called by both raw-GL UI passes (the Arc3D flush
 *       in {@code UIManager.render} and the dock present pass) so queued GUI and blur draws
 *       cannot sample an Arc3D texture or inherit its blend state.</li>
 * </ul>
 */
public final class BlazeStateSync {

    private BlazeStateSync() {
    }

    /**
     * Shared epilogue for a raw-GL UI pass: unbinds samplers and textures on units 0–3 and
     * restores the default GUI blend func/equation, through both the cached wrappers and raw GL.
     * A superset is safe at every call site (redundant unbinds are idempotent); a reset present
     * at only one site is a one-frame HUD corruption waiting on the other path.
     */
    public static void resetSamplersBlendTextures() {
        for (int i = 0; i <= 3; i++) {
            GL33C.glBindSampler(i, 0);
        }
        GlStateManager._blendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                GL33C.GL_ONE, GL33C.GL_ZERO);
        GL33C.glBlendFuncSeparate(GL33C.GL_SRC_ALPHA, GL33C.GL_ONE_MINUS_SRC_ALPHA,
                GL33C.GL_ONE, GL33C.GL_ZERO);
        GL33C.glBlendEquation(GL33C.GL_FUNC_ADD);
        for (int i = 3; i >= 0; i--) {
            GlStateManager._activeTexture(GL33C.GL_TEXTURE0 + i);
            GlStateManager._bindTexture(0);
            GL33C.glBindTexture(GL33C.GL_TEXTURE_2D, 0);
        }
        GL33C.glActiveTexture(GL33C.GL_TEXTURE0);
    }

    /** Call after any raw-GL block that ran outside Blaze3D's wrappers. */
    public static void resyncAfterRawGl() {
        GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER,
                GlStateManager.getFrameBuffer(GL33C.GL_READ_FRAMEBUFFER));
        GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER,
                GlStateManager.getFrameBuffer(GL33C.GL_DRAW_FRAMEBUFFER));
        GlStateManager._colorMask(true, true, true, true);
        GL33C.glColorMask(true, true, true, true);
        GlStateManager._disableCull();
        GL33C.glDisable(GL33C.GL_CULL_FACE);
        invalidateProgramCache();
    }

    /** Forces the next Blaze3D draw to re-bind its shader program for real. */
    public static void invalidateProgramCache() {
        GlDevice device = (GlDevice) RenderSystem.getDevice();
        ((GlCommandEncoderAccessor) device.createCommandEncoder()).mcsx$setLastProgram(null);
    }
}
