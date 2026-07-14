package fr.lacaleche.glue.client.render.internal.material;

import com.mojang.blaze3d.pipeline.RenderTarget;
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

final class SodiumTerrainMaterialCapture implements TerrainMaterialCapture {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/material-buffer");

    private final MaterialCaptureTarget target = new MaterialCaptureTarget("Glue Sodium terrain material");
    private boolean frameActive;
    private boolean available;
    private boolean passAttached;
    private boolean loggedActive;
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
        target.prepare(main);
        target.clear();
        frameActive = true;
    }

    @Override
    public void cancelFrame() {
        frameActive = false;
        available = false;
        if (passAttached) forceRestoreRecordedFramebuffer();
    }

    void beginPass(Object renderPass) {
        if (!frameActive || passAttached || !SodiumMaterialShaderPatch.isReady()) return;
        if (isPassTranslucent(renderPass)) return;

        int boundFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int materialTexture = target.colorTextureId();
        if (boundFramebuffer <= 0 || materialTexture <= 0) return;
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
                GL11.GL_TEXTURE_2D, materialTexture, 0);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});

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

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (main == null || main.getDepthTextureView() == null) return;
        target.copyDepthFrom(main);
        available = true;
        if (!loggedActive) {
            loggedActive = true;
            LOGGER.info("Sodium 0.7.3 terrain material adapter active");
        }
    }

    @Override
    public int colorTextureId() {
        return available ? target.colorTextureId() : -1;
    }

    @Override
    public int depthTextureId() {
        return available ? target.depthTextureId() : -1;
    }

    @Override
    public void cleanup() {
        cancelFrame();
        target.cleanup();
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
