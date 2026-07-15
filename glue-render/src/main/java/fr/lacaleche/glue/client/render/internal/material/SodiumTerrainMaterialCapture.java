package fr.lacaleche.glue.client.render.internal.material;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.render.internal.gbuffer.GBufferCapture;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Captures Sodium's opaque terrain material into a second color attachment during its terrain pass.
 *
 * <p>When material never lands (grainy fallback everywhere), check these silent-failure points in order:
 * <ol>
 *   <li>{@code SodiumShaderChunkRendererMixin} begin/end injects (require = 0) may not match this Sodium
 *       build's method signatures, so {@link #beginPass}/{@link #endPass} are never called (total silence,
 *       not even the "material hook fired" line below);</li>
 *   <li>{@code SodiumShaderLoaderMixin} / {@code SodiumMaterialShaderPatch} anchors may not match, logging
 *       "does not match Sodium 0.7.3" and leaving {@code isReady()} false forever;</li>
 *   <li>the MRT attach or framebuffer-completeness check may fail, logging "disabled: ...";</li>
 *   <li>even after "adapter active" logs, the consumer's depth gate
 *       {@code abs(materialDepth - sceneDepth) < 1e-5} in deferred.fsh / composite.fsh can reject every
 *       pixel if the copied depth is not bit-identical to the main depth.</li>
 * </ol>
 */
final class SodiumTerrainMaterialCapture implements TerrainMaterialCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/material-buffer");

    private boolean frameActive;
    private boolean available;
    private boolean passAttached;
    private boolean loggedActive;
    private boolean loggedHookFired;
    private boolean loggedWaitingForPatch;
    private boolean warnedFramebuffer;
    private boolean warnedReflection;
    private int framebuffer;
    private int[] drawBuffers;
    private Method isTranslucent;

    @Override
    public void beginFrame(long sequence) {
        available = false;
        frameActive = false;
        if (passAttached) {
            forceRestoreRecordedFramebuffer();
            disable("previous terrain pass ended without restoring its framebuffer");
            return;
        }
        if (SodiumMaterialShaderPatch.isRejected() || RenderCompat.isIrisShaderEnabled()) return;

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getDepthTextureView() == null) return;
        frameActive = true;
    }

    @Override
    public void cancelFrame() {
        frameActive = false;
        available = false;
        if (passAttached) forceRestoreRecordedFramebuffer();
    }

    void beginPass(Object renderPass) {
        if (!loggedHookFired) {
            loggedHookFired = true;
            LOGGER.info("Sodium material hook fired; waiting for shader patch + opaque pass");
        }
        if (!frameActive || passAttached) return;
        if (!SodiumMaterialShaderPatch.isReady()) {
            if (!loggedWaitingForPatch) {
                loggedWaitingForPatch = true;
                LOGGER.info("Sodium material capture waiting: shader patch not applied yet "
                        + "(SodiumMaterialShaderPatch.isReady() == false); both terrain shaders must be "
                        + "patched before capture can attach");
            }
            return;
        }
        if (isPassTranslucent(renderPass)) return;

        int boundFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        // Route terrain material into the shared material G-buffer (the same albedo+normal and
        // id+depth attachments entities and particles fill), so all opaque surfaces land in one
        // buffer under one id contract. -1 until the G-buffer FBO is ready this frame.
        int albedoNormal = GBufferCapture.albedoNormalTextureId();
        int materialId = GBufferCapture.materialIdTextureId();
        if (boundFramebuffer <= 0 || albedoNormal <= 0 || materialId <= 0) return;
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || boundFramebuffer != FramebufferHelper.getFramebufferId(main)) {
            disable("opaque terrain is not drawing into Minecraft's main framebuffer");
            return;
        }

        int attachmentType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (attachmentType != GL11.GL_NONE) {
            disable("color attachment 1 is already owned by another renderer");
            return;
        }

        framebuffer = boundFramebuffer;
        drawBuffers = readDrawBuffers();
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL11.GL_TEXTURE_2D, albedoNormal, 0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                GL11.GL_TEXTURE_2D, materialId, 0);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1,
                GL30.GL_COLOR_ATTACHMENT2});

        int status = GL30.glCheckFramebufferStatus(GL30.GL_DRAW_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            restoreFramebuffer();
            disable("framebuffer is incomplete after attaching the material target (0x"
                    + Integer.toHexString(status) + ")");
            return;
        }
        passAttached = true;
    }

    void endPass() {
        if (!passAttached) return;
        boolean restored = restoreFramebuffer();
        passAttached = false;
        if (!restored) return;

        available = true;
        if (!loggedActive) {
            loggedActive = true;
            LOGGER.info("Sodium 0.7.3 terrain material adapter active (routed to shared G-buffer)");
        }
    }

    // Terrain material now lives in the shared G-buffer (id 1), not a separate texture, so the
    // legacy depth-matched consumer path reports nothing for Sodium -- the shaders read terrain
    // from the G-buffer id attachment instead.
    @Override
    public int colorTextureId() {
        return -1;
    }

    @Override
    public int depthTextureId() {
        return -1;
    }

    @Override
    public void cleanup() {
        cancelFrame();
    }

    private boolean isPassTranslucent(Object renderPass) {
        try {
            if (isTranslucent == null) isTranslucent = renderPass.getClass().getMethod("isTranslucent");
            return (boolean) isTranslucent.invoke(renderPass);
        } catch (ReflectiveOperationException exception) {
            disable("cannot inspect terrain pass", exception);
            return true;
        }
    }

    private boolean restoreFramebuffer() {
        int boundFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (boundFramebuffer != framebuffer) {
            int unexpectedFramebuffer = boundFramebuffer;
            if (framebuffer != 0) {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                restoreAttachment();
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, unexpectedFramebuffer);
                disable("draw framebuffer changed during Sodium's terrain pass");
            }
            framebuffer = 0;
            drawBuffers = null;
            return false;
        }
        restoreAttachment();
        framebuffer = 0;
        drawBuffers = null;
        return true;
    }

    private void restoreAttachment() {
        if (drawBuffers != null && drawBuffers.length > 0) GL20.glDrawBuffers(drawBuffers);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1,
                GL11.GL_TEXTURE_2D, 0, 0);
        GL30.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2,
                GL11.GL_TEXTURE_2D, 0, 0);
    }

    private void forceRestoreRecordedFramebuffer() {
        int currentFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        if (framebuffer != 0) {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            restoreAttachment();
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, currentFramebuffer);
        }
        passAttached = false;
        framebuffer = 0;
        drawBuffers = null;
    }

    private void disable(String reason) {
        frameActive = false;
        available = false;
        if (!warnedFramebuffer) {
            warnedFramebuffer = true;
            LOGGER.error("Sodium material adapter disabled: {}", reason);
        }
    }

    private void disable(String reason, Exception exception) {
        frameActive = false;
        available = false;
        if (!warnedReflection) {
            warnedReflection = true;
            LOGGER.error("Sodium material adapter disabled: {}", reason, exception);
        }
    }

    private static int[] readDrawBuffers() {
        int count = Math.min(GL11.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS), 8);
        int[] buffers = new int[count];
        for (int index = 0; index < count; index++) {
            buffers[index] = GL11.glGetInteger(GL20.GL_DRAW_BUFFER0 + index);
        }
        int used = count;
        while (used > 1 && buffers[used - 1] == GL11.GL_NONE) used--;
        return Arrays.copyOf(buffers, used);
    }
}
