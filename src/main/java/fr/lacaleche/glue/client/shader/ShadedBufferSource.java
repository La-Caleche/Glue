package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.mixin.CompositeRenderTypeAccessor;
import fr.lacaleche.glue.client.mixin.CompositeStateAccessor;
import fr.lacaleche.glue.client.mixin.TextureStateShardAccessor;
import fr.lacaleche.glue.client.shader.internal.NoopVertexConsumer;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * A per-call {@link MultiBufferSource} that re-routes vertex data through a
 * {@link GluePipeline} and, when necessary, captures the result to an FBO
 * for blend-correct compositing.
 *
 * <p>Global render state (FBO pool, composite queue, capture flags) lives in
 * {@link ShaderContext#INSTANCE} — this class holds only per-call instance state.</p>
 *
 * <p>Must be used in a try-with-resources block:</p>
 * <pre>{@code
 * try (ShadedBufferSource source = pipeline.wrap()) {
 *     // draw calls...
 *     source.endBatch();
 * }
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class ShadedBufferSource implements MultiBufferSource, AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/shader");
    private static final int DEFAULT_BUFFER_SIZE = GlueConstants.DEFAULT_BUFFER_SIZE;

    private final GluePipeline pipeline;
    private final boolean additive;
    private final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers = new LinkedHashMap<>();
    private final ByteBufferBuilder sharedBuffer = new ByteBufferBuilder(DEFAULT_BUFFER_SIZE);
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
    public VertexConsumer getBuffer(RenderType renderType) {
        ResourceLocation texture = extractTexture(renderType);

        if (texture != null) {
            RenderType shadedType = pipeline.renderType(texture);
            fixedBuffers.computeIfAbsent(shadedType, rt -> new ByteBufferBuilder(DEFAULT_BUFFER_SIZE));
            return ownSource.getBuffer(shadedType);
        }

        String typeName = renderType.getClass().getSimpleName();
        if (ShaderContext.get().getWarnedNoopTypes().add(typeName)) {
            LOGGER.debug("[Glue] ShadedBufferSource: dropping draw for unsupported RenderType '{}' ({})", renderType, typeName);
        }
        return NoopVertexConsumer.INSTANCE;
    }

    public void endBatch() {
        RenderSystem.assertOnRenderThread();
        if (useIsolatedCapture) {
            endBatchIsolated();
        } else if (RenderCompat.isIrisShaderEnabled()) {
            // Iris path: skip depth copy when additive (we want the captured
            // sprite to draw unconditionally; final blit composites additively).
            captureWithPooledFbo(!additive, true, "iris");
        } else if (additive) {
            // Vanilla additive: DO copy depth so the sprite is properly
            // occluded by terrain during capture. The deferred blit later
            // discards near-black pixels and composites additively, after
            // entities and clouds have already rendered.
            captureWithPooledFbo(true, false, "vanilla-additive");
        } else {
            ownSource.endBatch();
        }
    }

    private void captureWithPooledFbo(boolean copyDepth, boolean withIrisBypass, String pathLabel) {
        ShaderContext ctx = ShaderContext.get();
        RenderTarget targetFbo = ctx.acquirePooledFbo();
        FramebufferHelper.clear(targetFbo, 0f, 0f, 0f, 0f);
        ctx.enqueueComposite(targetFbo, additive);

        ctx.setShouldCopyDepth(copyDepth);
        ctx.setDepthAlreadyCopied(false);
        ctx.setCapturing(true, FramebufferHelper.getFramebufferId(targetFbo));
        try {
            if (withIrisBypass) {
                RenderCompat.withIrisBypass(ownSource::endBatch);
            } else {
                ownSource.endBatch();
            }
        } finally {
            ctx.setCapturing(false, 0);
        }

        if (!ctx.isBlitPathLogged()) {
            ctx.markBlitPathLogged();
            LOGGER.info("[Glue-Path] {} capture using pooled FBO (additive={})", pathLabel, additive);
        }
    }

    /**
     * Isolated capture: renders to a private FBO regardless of Iris state.
     * The captured result is accessible via {@link ShaderContext#getIsolatedTarget()}.
     */
    private void endBatchIsolated() {
        ShaderContext ctx = ShaderContext.get();
        Minecraft mc = Minecraft.getInstance();
        int w = mc.getWindow().getWidth();
        int h = mc.getWindow().getHeight();

        ctx.setIsolatedFbo(FramebufferHelper.resizeOrCreate(ctx.getIsolatedTarget(), w, h));
        FramebufferHelper.clear(ctx.getIsolatedTarget(), 0f, 0f, 0f, 0f);

        ctx.setIsolatedCapture(true);
        try {
            if (RenderCompat.isIrisShaderEnabled()) {
                RenderCompat.withIrisBypass(ownSource::endBatch);
            } else {
                ownSource.endBatch();
            }
        } finally {
            ctx.setIsolatedCapture(false);
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
