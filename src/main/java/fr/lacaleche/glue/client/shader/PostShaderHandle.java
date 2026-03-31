package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

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

        RenderCompat.withIrisBypass(() -> chain.process(target, resourceAllocator));
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

