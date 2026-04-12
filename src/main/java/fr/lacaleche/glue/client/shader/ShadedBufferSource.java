package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.mixin.CompositeRenderTypeAccessor;
import fr.lacaleche.glue.client.mixin.CompositeStateAccessor;
import fr.lacaleche.glue.client.mixin.TextureStateShardAccessor;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Environment(EnvType.CLIENT)
public class ShadedBufferSource implements MultiBufferSource, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-capture");

    // ── Alpha capture FBO (standard entity shaders) ──
    private static RenderTarget captureFboAlpha;
    private static boolean frameClearedAlpha = false;
    private static boolean depthCopiedAlpha = false;

    // ── Additive capture FBO (additive VFX) ──
    private static RenderTarget captureFboAdditive;
    private static boolean frameClearedAdditive = false;
    private static boolean depthCopiedAdditive = false;

    // ── Active capture state (read by mixin) ──
    private static volatile boolean capturing = false;
    private static int activeCaptureTargetFboId = 0;
    private static boolean activeCaptureCopyDepth = true;

    private static boolean blitPathLogged = false;
    private static int sceneDepthTextureId = -1;
    private static final Set<String> warnedNoopTypes = new HashSet<>();

    // ── Isolated capture (post-effects) ──
    private static volatile boolean isolatedCapture = false;
    private static RenderTarget isolatedFbo;

    private final GluePipeline pipeline;
    private final boolean additive;
    private final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers = new LinkedHashMap<>();
    private final ByteBufferBuilder sharedBuffer = new ByteBufferBuilder(1536);
    private final MultiBufferSource.BufferSource ownSource;

    private final boolean useIsolatedCapture;

    public ShadedBufferSource(GluePipeline pipeline) {
        this(pipeline, false);
    }

    public ShadedBufferSource(GluePipeline pipeline, boolean isolatedCapture) {
        this.pipeline = pipeline;
        this.additive = pipeline.isAdditive();
        this.useIsolatedCapture = isolatedCapture;
        this.ownSource = MultiBufferSource.immediateWithBuffers(fixedBuffers, sharedBuffer);
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        ResourceLocation texture = extractTexture(renderType);

        if (texture != null) {
            RenderType shadedType = pipeline.entityType(texture);
            fixedBuffers.computeIfAbsent(shadedType, rt -> new ByteBufferBuilder(1536));
            return ownSource.getBuffer(shadedType);
        }

        String typeName = renderType.getClass().getSimpleName();
        if (warnedNoopTypes.add(typeName)) {
            LOGGER.debug("[Glue] ShadedBufferSource: dropping draw for unsupported RenderType '{}' ({})", renderType, typeName);
        }
        return NoopVertexConsumer.INSTANCE;
    }

    public void endBatch() {
        if (useIsolatedCapture) {
            endBatchIsolated();
        } else if (RenderCompat.isIrisShaderEnabled()) {
            endBatchIris();
        } else {
            ownSource.endBatch();
        }
    }

    private void endBatchIris() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        // Select the correct capture FBO based on blend mode
        RenderTarget targetFbo;
        if (additive) {
            captureFboAdditive = FramebufferHelper.resizeOrCreate(captureFboAdditive, w, h);
            if (!frameClearedAdditive) {
                FramebufferHelper.clear(captureFboAdditive, 0f, 0f, 0f, 0f);
                frameClearedAdditive = true;
            }
            targetFbo = captureFboAdditive;
            activeCaptureCopyDepth = !depthCopiedAdditive;
        } else {
            captureFboAlpha = FramebufferHelper.resizeOrCreate(captureFboAlpha, w, h);
            if (!frameClearedAlpha) {
                FramebufferHelper.clear(captureFboAlpha, 0f, 0f, 0f, 0f);
                frameClearedAlpha = true;
            }
            targetFbo = captureFboAlpha;
            activeCaptureCopyDepth = !depthCopiedAlpha;
        }

        // Set the active capture target for the mixin to read
        activeCaptureTargetFboId = FramebufferHelper.getFramebufferId(targetFbo);
        capturing = true;
        try {
            RenderCompat.withIrisBypass(ownSource::endBatch);
        } finally {
            capturing = false;
            activeCaptureTargetFboId = 0;
            if (additive) {
                depthCopiedAdditive = true;
            } else {
                depthCopiedAlpha = true;
            }
        }

        if (!blitPathLogged) {
            blitPathLogged = true;
            LOGGER.info("[Glue-Path] blend-aware blit from PostCompositeMixin (additive={})", additive);
        }
    }

    /**
     * Isolated capture: renders to a private FBO regardless of Iris state.
     * The mixin redirects via isIsolatedCapturing(). The captured result
     * is in isolatedFbo, accessible via getIsolatedTarget().
     */
    private void endBatchIsolated() {
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        isolatedFbo = FramebufferHelper.resizeOrCreate(isolatedFbo, w, h);
        FramebufferHelper.clear(isolatedFbo, 0f, 0f, 0f, 0f);

        isolatedCapture = true;
        try {
            if (RenderCompat.isIrisShaderEnabled()) {
                RenderCompat.withIrisBypass(ownSource::endBatch);
            } else {
                ownSource.endBatch();
            }
        } finally {
            isolatedCapture = false;
        }
    }

    public static boolean isCapturing() {
        return capturing;
    }

    public static boolean isIsolatedCapturing() {
        return isolatedCapture;
    }

    /**
     * Returns the currently active capture FBO ID.
     * Called by {@code GlueDrawBufferFixMixin} during draw redirect.
     */
    public static int getCaptureFboId() {
        return activeCaptureTargetFboId;
    }

    /**
     * Whether the mixin should copy scene depth to the active capture FBO.
     */
    public static boolean shouldCopyDepth() {
        return activeCaptureCopyDepth;
    }

    public static int getIsolatedFboId() {
        if (isolatedFbo == null) return 0;
        return FramebufferHelper.getFramebufferId(isolatedFbo);
    }

    /**
     * Returns the isolated capture RenderTarget for post-processing.
     */
    public static RenderTarget getIsolatedTarget() {
        return isolatedFbo;
    }

    public static void setSceneDepthTextureId(int id) {
        sceneDepthTextureId = id;
    }

    public static int getSceneDepthTextureId() {
        return sceneDepthTextureId;
    }

    public static boolean isDepthCopied() {
        // Used by mixin — check the active capture's depth state
        return !activeCaptureCopyDepth;
    }

    public static void setDepthCopied(boolean value) {
        // After mixin copies depth, mark it done
        activeCaptureCopyDepth = !value;
    }

    // ── Blit: composites both capture FBOs onto the main framebuffer ──

    /**
     * Called by {@code GluePostCompositeMixin} after Iris compositing.
     * Blits both FBOs with their correct blend modes.
     */
    public static void postCompositeBlit() {
        // Alpha content first (drawn behind)
        if (frameClearedAlpha) {
            blitCaptureToScreen(captureFboAlpha, false);
            frameClearedAlpha = false;
            depthCopiedAlpha = false;
        }

        // Additive content on top
        if (frameClearedAdditive) {
            blitCaptureToScreen(captureFboAdditive, true);
            frameClearedAdditive = false;
            depthCopiedAdditive = false;
        }
    }

    private static void blitCaptureToScreen(RenderTarget fbo, boolean additive) {
        if (fbo == null) return;

        int captureColorId = FramebufferHelper.getColorTextureId(fbo);
        int captureDepthId = FramebufferHelper.getDepthTextureId(fbo);
        if (captureColorId <= 0 || captureDepthId <= 0) return;

        GlDirectRenderer.blitCapture(captureColorId, captureDepthId, sceneDepthTextureId, additive);
    }

    private static ResourceLocation extractTexture(RenderType renderType) {
        if (!(renderType instanceof RenderType.CompositeRenderType compositeType)) {
            return null;
        }

        try {
            RenderType.CompositeState state = ((CompositeRenderTypeAccessor) (Object) compositeType).glue$getState();
            RenderStateShard.EmptyTextureStateShard textureShard = ((CompositeStateAccessor) (Object) state).glue$getTextureState();
            Optional<ResourceLocation> texture = ((TextureStateShardAccessor) textureShard).glue$getCutoutTexture();
            return texture.orElse(null);
        } catch (Exception e) {
            LOGGER.debug("[Glue] Failed to extract texture from RenderType {}: {}", renderType.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    @Override
    public void close() {
        sharedBuffer.close();
        for (ByteBufferBuilder buffer : fixedBuffers.values()) {
            buffer.close();
        }
        fixedBuffers.clear();
    }
}
