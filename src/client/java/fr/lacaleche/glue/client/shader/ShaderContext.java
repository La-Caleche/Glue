package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.shader.internal.GlDirectRenderer;
import fr.lacaleche.glue.client.shader.internal.SceneDepthCapture;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final int UNRESOLVED = -1;

    private final List<RenderTarget> availableFbos = new ArrayList<>();
    private final List<QueuedCapture> compositeQueue = new ArrayList<>();

    private final Set<String> warnedNoopTypes = new HashSet<>();

    private final GlDirectRenderer renderer = new GlDirectRenderer();
    private final SceneDepthCapture sceneDepthCapture = new SceneDepthCapture();

    private boolean capturing = false;
    private int activeCaptureTargetFboId = 0;
    private boolean shouldCopyDepth = true;
    private boolean depthAlreadyCopied = false;
    private boolean blitPathLogged = false;
    private int sceneDepthTextureId = -1;
    private boolean isolatedCapture = false;
    private RenderTarget sceneDepthCaptureTarget;
    private int sceneDepthCaptureFboId = UNRESOLVED;
    private boolean sceneDepthRefusalReported = false;

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
        if (!capturing) {
            sceneDepthCaptureTarget = null;
            sceneDepthCaptureFboId = UNRESOLVED;
        }
    }

    /**
     * Makes the coming capture draw into {@code target}'s colour over the scene's own depth, instead of
     * the target's depth; cleared with the capture by {@link #setCapturing}.
     */
    @ApiStatus.Internal
    public void captureOverSceneDepth(RenderTarget target) {
        this.sceneDepthCaptureTarget = target;
        this.sceneDepthCaptureFboId = UNRESOLVED;
    }

    public boolean isCapturingOverSceneDepth() {
        return capturing && sceneDepthCaptureTarget != null;
    }

    /**
     * The framebuffer pairing the capture's colour with the scene depth, resolved on the capture's first
     * pass: Iris's main depth when a pack is active, else {@code boundDepthTextureId}, the depth of the
     * framebuffer the pass was set up on. 0 when no pairing is possible; the capture then draws unoccluded.
     */
    @ApiStatus.Internal
    public int sceneDepthCaptureFbo(int boundDepthTextureId) {
        if (sceneDepthCaptureFboId != UNRESOLVED) return sceneDepthCaptureFboId;
        int depthTextureId = RenderCompat.isIrisShaderEnabled()
                ? RenderCompat.getIrisMainDepthGlId()
                : boundDepthTextureId;
        int colorTextureId = FramebufferHelper.getColorTextureId(sceneDepthCaptureTarget);
        sceneDepthCaptureFboId = depthTextureId > 0 && colorTextureId > 0
                ? sceneDepthCapture.attach(colorTextureId, depthTextureId)
                : 0;
        if (sceneDepthCaptureFboId == 0 && !sceneDepthRefusalReported) {
            sceneDepthRefusalReported = true;
            LOGGER.warn("[Glue] No scene depth to test a capture against (depth texture {}): "
                    + "pipelines that do not write depth draw over terrain this session", depthTextureId);
        }
        return sceneDepthCaptureFboId;
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

    /**
     * Queues {@code fbo} for the composite after world rendering. {@code compareDepth} drops the pixels the
     * scene drew in front of the capture's own depth; a capture that wrote no depth was already tested.
     */
    @ApiStatus.Internal
    public void enqueueComposite(RenderTarget fbo, boolean additive, boolean compareDepth) {
        compositeQueue.add(new QueuedCapture(fbo, additive, compareDepth));
    }

    /**
     * Called by {@code GluePostCompositeMixin} after Iris compositing.
     * Blits all queued captures to the main framebuffer and returns FBOs to the pool.
     */
    public void postCompositeBlit() {
        for (QueuedCapture qc : compositeQueue) {
            blitCaptureToScreen(qc.fbo(), qc.additive(), qc.compareDepth());
            availableFbos.add(qc.fbo());
        }
        compositeQueue.clear();
    }

    private void blitCaptureToScreen(RenderTarget fbo, boolean additive, boolean compareDepth) {
        if (fbo == null) return;
        int captureColorId = FramebufferHelper.getColorTextureId(fbo);
        int captureDepthId = FramebufferHelper.getDepthTextureId(fbo);
        if (captureColorId <= 0 || captureDepthId <= 0) return;
        renderer.blitCapture(captureColorId, captureDepthId, sceneDepthTextureId, additive, compareDepth);
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
        sceneDepthCapture.cleanup();
        capturing = false;
        sceneDepthCaptureTarget = null;
        sceneDepthCaptureFboId = UNRESOLVED;
        sceneDepthRefusalReported = false;
        isolatedCapture = false;
        blitPathLogged = false;
        renderer.cleanup();
    }

    record QueuedCapture(RenderTarget fbo, boolean additive, boolean compareDepth) {
    }
}
