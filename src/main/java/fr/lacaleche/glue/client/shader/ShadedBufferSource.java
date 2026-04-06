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

/**
 * Iris-compatible custom shader rendering via FBO capture + depth-aware blit.
 *
 * <p><b>Strategy:</b></p>
 * <ol>
 *   <li>During entity rendering: flush with bypass=true; a mixin copies scene depth
 *       into a private FBO and redirects the draw (with depth test + no blending)</li>
 *   <li>Iris's composite pass only touches the scene FBO—our capture FBO is untouched</li>
 *   <li>At LAST: blit the capture FBO to screen via a depth-aware shader that
 *       discards pixels behind scene geometry (hand, blocks above, etc.)</li>
 * </ol>
 */
@Environment(EnvType.CLIENT)
public class ShadedBufferSource implements MultiBufferSource {

    private final MultiBufferSource fallbackDelegate;
    private final GluePipeline pipeline;

    private final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers = new LinkedHashMap<>();
    private final ByteBufferBuilder sharedBuffer = new ByteBufferBuilder(1536);
    private final MultiBufferSource.BufferSource ownSource;

    // ── Static capture FBO state ──────────────────────────────────
    private static RenderTarget captureFbo;
    private static volatile boolean capturing = false;
    private static boolean blitPathLogged = false;
    private static boolean frameCleared = false;
    private static boolean depthCopied = false;
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-capture");

    public ShadedBufferSource(MultiBufferSource fallbackDelegate, GluePipeline pipeline) {
        this.fallbackDelegate = fallbackDelegate;
        this.pipeline = pipeline;
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

        // No texture extracted — skip this layer entirely (e.g. translucent glow)
        // to prevent unshaded full quads from rendering as black squares
        return NoopVertexConsumer.INSTANCE;
    }

    /**
     * Flushes with Iris FBO capture: draws to private FBO, then blits at LAST
     * with depth-aware occlusion.
     */
    public void endBatch() {
        if (RenderCompat.isIrisShaderEnabled()) {
            Minecraft mc = Minecraft.getInstance();
            int w = mc.getWindow().getWidth();
            int h = mc.getWindow().getHeight();

            // Ensure capture FBO exists and matches screen size
            captureFbo = FramebufferHelper.resizeOrCreate(captureFbo, w, h);

            // Clear only once per frame — subsequent endBatch() calls accumulate
            if (!frameCleared) {
                FramebufferHelper.clear(captureFbo, 0f, 0f, 0f, 0f);
                frameCleared = true;
            }

            // Mark capture mode — mixin will redirect draws to our FBO
            capturing = true;
            try {
                RenderCompat.withIrisBypass(ownSource::endBatch);
            } finally {
                capturing = false;
            }

            // With Iris: blit from GluePostCompositeMixin (AFTER Iris composites)
            // Without Iris: blit at LAST (adequate timing)
            // NOTE: hand occlusion with Iris is a known limitation — Iris clears FBO 3 depth
            boolean irisActive = RenderCompat.isIrisShaderEnabled();
            if (!blitPathLogged) {
                blitPathLogged = true;
                LOGGER.info("[Glue-Path] irisActive={}, blit from {}",
                        irisActive, irisActive ? "PostCompositeMixin" : "DeferredDrawQueue.LAST");
            }
            if (!irisActive) {
                DeferredDrawQueue.defer(() -> {
                    blitCaptureToScreen();
                    frameCleared = false;
                    depthCopied = false;
                });
            }
            // With Iris: postCompositeBlit() called by GluePostCompositeMixin

        } else {
            ownSource.endBatch();
        }
    }

    // ── Static accessors for the mixin ───────────────────────────

    private static int sceneDepthTextureId = -1;

    public static boolean isCapturing() {
        return capturing;
    }

    public static int getCaptureFboId() {
        if (captureFbo == null) return 0;
        return FramebufferHelper.getFramebufferId(captureFbo);
    }

    /** Called from the mixin to save Iris's entity-rendering FBO depth texture. */
    public static void setSceneDepthTextureId(int id) {
        sceneDepthTextureId = id;
    }

    public static int getSceneDepthTextureId() {
        return sceneDepthTextureId;
    }

    public static boolean isDepthCopied() {
        return depthCopied;
    }

    public static void setDepthCopied(boolean value) {
        depthCopied = value;
    }

    /**
     * Blits the capture FBO to MC's main render target with depth-aware occlusion.
     */
    private static void blitCaptureToScreen() {
        if (captureFbo == null) return;

        int captureColorId = FramebufferHelper.getColorTextureId(captureFbo);
        int captureDepthId = FramebufferHelper.getDepthTextureId(captureFbo);
        if (captureColorId <= 0 || captureDepthId <= 0) return;

        GlDirectRenderer.blitCapture(captureColorId, captureDepthId, sceneDepthTextureId);
    }

    /**
     * Called from GluePostCompositeMixin AFTER GameRenderer.renderLevel() returns.
     * At this point Iris has finished ALL compositing, so our blit won't be overwritten.
     */
    public static void postCompositeBlit() {
        if (!frameCleared) return;
        blitCaptureToScreen();
        frameCleared = false;
        depthCopied = false;
    }

    /**
     * Extracts the texture {@link ResourceLocation} from a RenderType.
     */
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
            return null;
        }
    }
}
