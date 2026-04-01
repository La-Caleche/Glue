package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.mixin.PostChainAccessor;
import fr.lacaleche.glue.client.mixin.PostPassAccessor;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A handle to a lazily-loaded post-processing {@link PostChain}.
 * <p>
 * In MC 1.21.8, post-processing shaders are loaded through the {@code ShaderManager}
 * from JSON resources at {@code post_effect/<id>.json}. This handle wraps
 * the lifecycle of fetching and applying a post chain.
 *
 * <pre>{@code
 * PostShaderHandle grayscale = postRegistry.register("grayscale");
 *
 * // In a render event:
 * grayscale.apply(minecraft.getMainRenderTarget(), resourcePool);
 *
 * // With dynamic uniforms:
 * grayscale.setUniform("MyConfig", builder -> {
 *     builder.putFloat(intensity);
 *     builder.putFloat(strength);
 * });
 * }</pre>
 */
@Environment(EnvType.CLIENT)
public class PostShaderHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger("PostShaderHandle");

    private final ResourceLocation id;
    private final Set<ResourceLocation> externalTargets;
    private boolean warnedOnce = false;

    /**
     * @param id              The post effect resource location (maps to {@code post_effect/<id>.json})
     * @param externalTargets The set of external render targets this chain expects
     */
    public PostShaderHandle(ResourceLocation id, Set<ResourceLocation> externalTargets) {
        this.id = id;
        this.externalTargets = externalTargets;
    }

    /**
     * @return The resource location of this post effect
     */
    public ResourceLocation getId() {
        return this.id;
    }

    /**
     * @return The set of external targets this chain expects
     */
    public Set<ResourceLocation> getExternalTargets() {
        return this.externalTargets;
    }

    /**
     * Gets the compiled {@link PostChain} from the shader manager.
     * May return {@code null} if the shader failed to compile or the resource is missing.
     *
     * @return The post chain, or null
     */
    @Nullable
    public PostChain get() {
        return Minecraft.getInstance().getShaderManager().getPostChain(this.id, this.externalTargets);
    }

    /**
     * Updates a uniform buffer block across all passes in this post chain.
     * <p>
     * The consumer receives a {@link Std140Builder} and must write values in the
     * exact same order and types as declared in the GLSL {@code layout(std140) uniform} block.
     * A new {@link GpuBuffer} is created and replaces the existing one (the old buffer is closed).
     *
     * <pre>{@code
     * handle.setUniform("ShatteredConfig", builder -> {
     *     builder.putFloat(intensity);       // Intensity
     *     builder.putFloat(maxOffset);       // MaxOffset
     *     builder.putFloat(chromaStrength);  // ChromaticStrength
     * });
     * }</pre>
     *
     * @param blockName   The UBO block name as declared in the JSON uniforms (e.g. "ShatteredConfig")
     * @param bufferSize  The size of the std140 buffer in bytes
     * @param writer      Consumer that writes uniform values into the Std140Builder
     */
    public void setUniform(String blockName, int bufferSize, Consumer<Std140Builder> writer) {
        PostChain chain = this.get();
        if (chain == null) return;

        List<PostPass> passes = ((PostChainAccessor) chain).glue$getPasses();

        for (PostPass pass : passes) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).glue$getCustomUniforms();
            GpuBuffer existing = uniforms.get(blockName);
            if (existing == null) continue;

            // Build the new UBO data
            GpuBuffer newBuffer;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Std140Builder builder = Std140Builder.onStack(stack, bufferSize);
                writer.accept(builder);
                newBuffer = RenderSystem.getDevice().createBuffer(
                        () -> this.id + " / " + blockName,
                        GpuBuffer.USAGE_UNIFORM,
                        builder.get()
                );
            }

            // Replace the old buffer and close it
            existing.close();
            uniforms.put(blockName, newBuffer);
        }
    }

    /**
     * Applies this post-processing chain to the given render target.
     * <p>
     * Automatically bypasses Iris's pipeline override so the post shader
     * compiles with vanilla GLSL instead of being intercepted by shader packs.
     * After processing, rebinds the main render target to prevent leaving
     * Iris's framebuffer state corrupted.
     *
     * @param target             The render target to process (typically the main framebuffer)
     * @param resourceAllocator  The resource allocator for temporary resources
     */
    public void apply(RenderTarget target, GraphicsResourceAllocator resourceAllocator) {
        PostChain chain = this.get();
        if (chain == null) {
            if (!warnedOnce) {
                LOGGER.warn("[Glue] Post chain '{}' is null — shader may have failed to compile or resource is missing", this.id);
                warnedOnce = true;
            }
            return;
        }

        // Save the complete GL state that PostChain.process() may corrupt.
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        // When Iris is active, the currently-bound FBO (Iris's composite output) uses
        // DIFFERENT textures than mc.getMainRenderTarget(). PostChain reads from
        // target.getColorTexture(), which doesn't contain the current scene.
        // Fix: blit the scene FROM Iris's FBO TO the main render target before processing,
        // then blit the post-processed result back after.
        int mainFbo = FramebufferHelper.getFramebufferId(target);
        boolean needsBlit = RenderCompat.isIrisShaderEnabled() && mainFbo >= 0 && mainFbo != prevFbo;

        if (needsBlit) {
            int w = target.width;
            int h = target.height;

            // Copy scene from Iris's FBO → main render target's textures
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, mainFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }

        // Process the main render target (PostChain reads/writes target.getColorTexture())
        RenderCompat.withIrisBypass(() -> chain.process(target, resourceAllocator));

        if (needsBlit) {
            int w = target.width;
            int h = target.height;

            // Copy post-processed result from main render target → Iris's display FBO
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, mainFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, prevFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        }

        // Restore GL state so Iris's framebuffer and display pipeline remain intact
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, prevFbo);
        GL11.glViewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        if (prevDepthTest) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (prevBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
        if (prevScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }

    /**
     * Adds this post chain to a frame graph builder for deferred execution.
     *
     * @param frameGraphBuilder The frame graph builder
     * @param width             The screen width
     * @param height            The screen height
     * @param targetBundle      The target bundle containing render targets
     */
    public void addToFrame(FrameGraphBuilder frameGraphBuilder, int width, int height,
                           PostChain.TargetBundle targetBundle) {
        PostChain chain = this.get();
        if (chain != null) {
            chain.addToFrame(frameGraphBuilder, width, height, targetBundle);
        }
    }
}
