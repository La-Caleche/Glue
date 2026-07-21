package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.shader.internal.GlDirectRenderer;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Singleton managing the Glue shader render context: FBO pool, composite queue,
 * and capture state shared between {@link ShadedBufferSource} instances and the Glue mixins.
 *
 * <p>Accessed via {@link #get()}. Lifecycle is managed by {@code GlueClient}:
 * {@link #cleanup()} is called on {@code CLIENT_STOPPING}.</p>
 *
 * <p>Methods annotated with {@link ApiStatus.Internal} are mutators called from
 * {@link ShadedBufferSource} and the Glue mixins. Treat them as implementation
 * detail — do not call from mod code.</p>
 */
@Environment(EnvType.CLIENT)
public final class ShaderContext {

    private static final ShaderContext INSTANCE = new ShaderContext();

    private final List<RenderTarget> availableFbos = new ArrayList<>();
    private final List<QueuedCapture> compositeQueue = new ArrayList<>();

    private final Set<String> warnedNoopTypes = new HashSet<>();

    private final GlDirectRenderer renderer = new GlDirectRenderer();

    private boolean capturing = false;
    private int activeCaptureTargetFboId = 0;
    private boolean shouldCopyDepth = true;
    private boolean depthAlreadyCopied = false;
    private boolean blitPathLogged = false;
    private int sceneDepthTextureId = -1;
    private boolean isolatedCapture = false;

    /**
     * Permanent isolated-capture FBO — not pooled because callers (post-processors)
     * need to read it after {@code endBatch()} returns. Released in {@link #cleanup()}.
     */
    private RenderTarget isolatedFbo;

    private ShaderContext() {
    }

    public static ShaderContext get() {
        return INSTANCE;
    }

    public GlDirectRenderer getRenderer() {
        return renderer;
    }

    public boolean isCapturing() {
        return capturing;
    }

    public boolean isIsolatedCapturing() {
        return isolatedCapture;
    }

    public int getCaptureFboId() {
        return activeCaptureTargetFboId;
    }

    public boolean shouldCopyDepth() {
        return shouldCopyDepth;
    }

    public int getIsolatedFboId() {
        if (isolatedFbo == null) return 0;
        return FramebufferHelper.getFramebufferId(isolatedFbo);
    }

    public RenderTarget getIsolatedTarget() {
        return isolatedFbo;
    }

    public int getSceneDepthTextureId() {
        return sceneDepthTextureId;
    }

    @ApiStatus.Internal
    public void setSceneDepthTextureId(int id) {
        this.sceneDepthTextureId = id;
    }

    public boolean hasDepthBeenCopied() {
        return depthAlreadyCopied;
    }

    @ApiStatus.Internal
    public void markDepthCopied() {
        this.depthAlreadyCopied = true;
    }

    @ApiStatus.Internal
    public void setCapturing(boolean capturing, int captureFboId) {
        RenderSystem.assertOnRenderThread();
        if (capturing && this.capturing) {
            throw new IllegalStateException(
                    "Already capturing to FBO " + this.activeCaptureTargetFboId
                            + " — nested captures are not supported");
        }
        this.capturing = capturing;
        this.activeCaptureTargetFboId = capturing ? captureFboId : 0;
    }

    @ApiStatus.Internal
    public void setShouldCopyDepth(boolean shouldCopyDepth) {
        this.shouldCopyDepth = shouldCopyDepth;
    }

    @ApiStatus.Internal
    public void setDepthAlreadyCopied(boolean depthAlreadyCopied) {
        this.depthAlreadyCopied = depthAlreadyCopied;
    }

    @ApiStatus.Internal
    public void setIsolatedCapture(boolean isolatedCapture) {
        this.isolatedCapture = isolatedCapture;
    }

    @ApiStatus.Internal
    public void setIsolatedFbo(RenderTarget fbo) {
        this.isolatedFbo = fbo;
    }

    @ApiStatus.Internal
    public Set<String> getWarnedNoopTypes() {
        return warnedNoopTypes;
    }

    @ApiStatus.Internal
    public RenderTarget acquirePooledFbo() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();
        RenderTarget fbo = availableFbos.isEmpty()
                ? null
                : availableFbos.remove(availableFbos.size() - 1);
        return FramebufferHelper.resizeOrCreate(fbo, w, h);
    }

    @ApiStatus.Internal
    public void enqueueComposite(RenderTarget fbo, boolean additive) {
        compositeQueue.add(new QueuedCapture(fbo, additive));
    }

    /**
     * Called by {@code GluePostCompositeMixin} after Iris compositing.
     * Blits all queued captures to the main framebuffer and returns FBOs to the pool.
     */
    public void postCompositeBlit() {
        for (QueuedCapture qc : compositeQueue) {
            blitCaptureToScreen(qc.fbo(), qc.additive());
            availableFbos.add(qc.fbo());
        }
        compositeQueue.clear();
    }

    private void blitCaptureToScreen(RenderTarget fbo, boolean additive) {
        if (fbo == null) return;
        int captureColorId = FramebufferHelper.getColorTextureId(fbo);
        int captureDepthId = FramebufferHelper.getDepthTextureId(fbo);
        if (captureColorId <= 0 || captureDepthId <= 0) return;
        renderer.blitCapture(captureColorId, captureDepthId, sceneDepthTextureId, additive);
    }

    @ApiStatus.Internal
    public boolean isBlitPathLogged() {
        return blitPathLogged;
    }

    @ApiStatus.Internal
    public void markBlitPathLogged() {
        this.blitPathLogged = true;
    }

    /**
     * Destroys all pooled FBOs, any queued-but-unblitted FBOs, the isolated FBO,
     * and the GL renderer. Must be called on the render thread. Registered with
     * {@code ClientLifecycleEvents.CLIENT_STOPPING} by {@code GlueClient}.
     */
    public void cleanup() {
        RenderSystem.assertOnRenderThread();
        for (RenderTarget fbo : availableFbos) {
            fbo.destroyBuffers();
        }
        availableFbos.clear();
        for (QueuedCapture qc : compositeQueue) {
            if (qc.fbo() != null) qc.fbo().destroyBuffers();
        }
        compositeQueue.clear();
        if (isolatedFbo != null) {
            isolatedFbo.destroyBuffers();
            isolatedFbo = null;
        }
        capturing = false;
        isolatedCapture = false;
        blitPathLogged = false;
        renderer.cleanup();
    }

    record QueuedCapture(RenderTarget fbo, boolean additive) {
    }
}
