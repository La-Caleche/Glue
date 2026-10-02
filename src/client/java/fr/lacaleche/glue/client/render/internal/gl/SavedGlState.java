package fr.lacaleche.glue.client.render.internal.gl;

import com.mojang.blaze3d.opengl.GlStateManager;
import fr.lacaleche.glue.client.shader.internal.GlDirectRenderer;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.*;

/**
 * Captures and restores a comprehensive snapshot of the OpenGL state
 * that Glue's shader operations may modify.
 *
 * <p>Used by {@link GlDirectRenderer} and
 * {@link fr.lacaleche.glue.client.shader.PostShaderHandle PostShaderHandle}
 * to guarantee clean state around low-level GL calls.</p>
 */
@Environment(EnvType.CLIENT)
public record SavedGlState(
        int program, int drawFbo, int readFbo, int[] drawBuffers, int readBuffer,
        int vao, int arrayBuffer,
        boolean blend, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
        int blendEquationRgb, int blendEquationAlpha,
        boolean depth, boolean depthWrite, int depthFunc,
        boolean cull,
        boolean scissor,
        boolean colorRed, boolean colorGreen, boolean colorBlue, boolean colorAlpha,
        int activeTexture,
        int[] viewport, int[] scissorBox, float[] clearColor,
        int[] textures
) {
    /**
     * Number of texture units saved and restored, covering units 0..12 — the full range Glue's
     * passes bind (the deferred light pass reaches unit 12, {@code MaterialProps}). Vanilla and
     * Sodium rebind their samplers unconditionally, so this is defence for consumer mods and
     * Iris, which may leave a high unit active across a Glue pass.
     */
    private static final int TEXTURE_UNITS = 13;

    /**
     * How many texture units {@link GlStateManager} mirrors in its redundancy cache (indices
     * 0..11). Higher units exist at the GL level and Glue binds one &mdash; {@code MaterialProps}
     * at unit 12 &mdash; but the manager's texture-state array has exactly this many slots, so
     * binding a higher unit <em>through</em> it indexes past the array
     * ({@link ArrayIndexOutOfBoundsException}). {@link #restore()} routes those units through raw
     * GL instead, where there is no cache to keep truthful anyway.
     */
    private static final int MANAGED_TEXTURE_UNITS = 12;

    /** Compatibility accessor for operations that use one combined framebuffer binding. */
    public int fbo() {
        return drawFbo;
    }

    public static SavedGlState save() {
        int[] vp = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        int[] colorMask = new int[4];
        GL11.glGetIntegerv(GL11.GL_COLOR_WRITEMASK, colorMask);
        int[] scissorBox = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
        float[] clearColor = new float[4];
        GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
        int[] drawBuffers = readDrawBuffers();

        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        int[] textures = new int[TEXTURE_UNITS];
        for (int unit = 0; unit < TEXTURE_UNITS; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            textures[unit] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(activeTexture);

        return new SavedGlState(
                GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING),
                GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                drawBuffers,
                GL11.glGetInteger(GL11.GL_READ_BUFFER),
                GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING),
                GL11.glIsEnabled(GL11.GL_BLEND),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB),
                GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA),
                GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                GL11.glIsEnabled(GL11.GL_CULL_FACE),
                GL11.glIsEnabled(GL11.GL_SCISSOR_TEST),
                colorMask[0] != 0, colorMask[1] != 0, colorMask[2] != 0, colorMask[3] != 0,
                activeTexture,
                vp, scissorBox, clearColor,
                textures
        );
    }

    /**
     * Restores the snapshot <em>and</em> keeps Blaze3D's CPU-side state cache truthful.
     *
     * <p>Draws issued through vanilla pipelines between {@link #save()} and here (light-POV
     * re-renders, shadow bakes) update {@link GlStateManager}'s redundancy cache. Restoring the
     * cached states with raw GL would leave that cache describing the pass's final state while
     * the context holds the saved one; the next pipeline application then skips "already set"
     * state and renders with the wrong one. Symptom of exactly that: the GUI's cached item-icon
     * pre-renders coming out black for every icon first drawn after a Lumos Iris-mode frame.</p>
     *
     * <p>So every state {@code GlStateManager} caches (depth, blend enable/function, cull,
     * scissor enable, colour mask, active texture, per-unit 2D bindings, read/draw framebuffer
     * bindings) is restored through it, forced past the redundancy check by setting a throwaway
     * value first. Uncached state (program, VAO, draw/read buffers, viewport, scissor box, blend
     * equation, clear colour) stays raw.</p>
     */
    public void restore() {
        GL20.glUseProgram(program);
        bindFramebufferSynced(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
        bindFramebufferSynced(GL30.GL_READ_FRAMEBUFFER, readFbo);
        GL20.glDrawBuffers(drawBuffers);
        GL11.glReadBuffer(readBuffer);
        GL30.glBindVertexArray(vao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);

        if (depth) {
            GlStateManager._disableDepthTest();
            GlStateManager._enableDepthTest();
        } else {
            GlStateManager._enableDepthTest();
            GlStateManager._disableDepthTest();
        }
        GlStateManager._depthFunc(depthFunc == GL11.GL_ALWAYS ? GL11.GL_LEQUAL : GL11.GL_ALWAYS);
        GlStateManager._depthFunc(depthFunc);
        GlStateManager._depthMask(!depthWrite);
        GlStateManager._depthMask(depthWrite);

        boolean savedIsOnes = blendSrcRgb == GL11.GL_ONE && blendDstRgb == GL11.GL_ONE
                && blendSrcAlpha == GL11.GL_ONE && blendDstAlpha == GL11.GL_ONE;
        if (savedIsOnes) {
            GlStateManager._blendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA,
                    GL11.GL_ONE, GL11.GL_ZERO);
        } else {
            GlStateManager._blendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        }
        GlStateManager._blendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        if (blend) {
            GlStateManager._disableBlend();
            GlStateManager._enableBlend();
        } else {
            GlStateManager._enableBlend();
            GlStateManager._disableBlend();
        }

        if (cull) {
            GlStateManager._disableCull();
            GlStateManager._enableCull();
        } else {
            GlStateManager._enableCull();
            GlStateManager._disableCull();
        }
        if (scissor) {
            GlStateManager._disableScissorTest();
            GlStateManager._enableScissorTest();
        } else {
            GlStateManager._enableScissorTest();
            GlStateManager._disableScissorTest();
        }
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

        GlStateManager._colorMask(!colorRed, colorGreen, colorBlue, colorAlpha);
        GlStateManager._colorMask(colorRed, colorGreen, colorBlue, colorAlpha);
        GL11.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);

        // Units beyond GlStateManager's cache: restore with raw GL. Routing them through the
        // manager would index past its texture-state array. Done first so the managed loop below
        // runs last and leaves the manager's active-unit cache and the context in agreement.
        for (int unit = MANAGED_TEXTURE_UNITS; unit < textures.length; unit++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[unit]);
        }

        // The context's actual active unit may have drifted from the cache's idea of it; realign
        // the context to the cache first so _activeTexture's redundancy skip is trustworthy and
        // each _bindTexture below lands on the unit the cache records it against.
        GL13.glActiveTexture(GlStateManager._getActiveTexture());
        for (int unit = 0; unit < MANAGED_TEXTURE_UNITS; unit++) {
            GlStateManager._activeTexture(GL13.GL_TEXTURE0 + unit);
            GlStateManager._bindTexture(textures[unit] == 0 ? scratchTexture() : 0);
            GlStateManager._bindTexture(textures[unit]);
        }
        GlStateManager._activeTexture(activeTexture);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
    }

    /** Never-rendered scratch objects used as throwaway bind targets; one of each per process. */
    private static int scratchFbo = -1;
    private static int scratchTex = -1;

    private static void bindFramebufferSynced(int target, int fbo) {
        if (scratchFbo == -1) scratchFbo = GlStateManager.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(target, fbo == scratchFbo ? 0 : scratchFbo);
        GlStateManager._glBindFramebuffer(target, fbo);
    }

    private static int scratchTexture() {
        if (scratchTex == -1) scratchTex = GlStateManager._genTexture();
        return scratchTex;
    }

    private static int[] readDrawBuffers() {
        int count = Math.min(GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS), 8);
        int[] buffers = new int[count];
        for (int index = 0; index < count; index++) {
            buffers[index] = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index);
        }
        int used = count;
        while (used > 1 && buffers[used - 1] == GL11.GL_NONE) used--;
        return java.util.Arrays.copyOf(buffers, used);
    }
}
