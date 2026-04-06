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

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class PostShaderHandle {

    private static final Logger LOGGER = LoggerFactory.getLogger("PostShaderHandle");

    private final ResourceLocation id;
    private final Set<ResourceLocation> externalTargets;
    private boolean warnedOnce = false;

    public PostShaderHandle(ResourceLocation id, Set<ResourceLocation> externalTargets) {
        this.id = id;
        this.externalTargets = externalTargets;
    }

    public ResourceLocation getId() {
        return this.id;
    }

    public Set<ResourceLocation> getExternalTargets() {
        return this.externalTargets;
    }

    @Nullable
    public PostChain get() {
        return Minecraft.getInstance().getShaderManager().getPostChain(this.id, this.externalTargets);
    }

    public void setUniform(String blockName, int bufferSize, Consumer<Std140Builder> writer) {
        PostChain chain = this.get();
        if (chain == null) return;

        for (PostPass pass : ((PostChainAccessor) chain).glue$getPasses()) {
            Map<String, GpuBuffer> uniforms = ((PostPassAccessor) pass).glue$getCustomUniforms();
            GpuBuffer existing = uniforms.get(blockName);
            if (existing == null) continue;

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

            existing.close();
            uniforms.put(blockName, newBuffer);
        }
    }

    public void apply(RenderTarget target, GraphicsResourceAllocator resourceAllocator) {
        PostChain chain = this.get();
        if (chain == null) {
            if (!warnedOnce) {
                LOGGER.warn("[Glue] Post chain '{}' not available", this.id);
                warnedOnce = true;
            }
            return;
        }

        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);
        boolean prevDepthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean prevBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean prevScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        int mainFbo = FramebufferHelper.getFramebufferId(target);
        boolean needsBlit = RenderCompat.isIrisShaderEnabled() && mainFbo >= 0 && mainFbo != prevFbo;
        int w = target.width;
        int h = target.height;

        if (needsBlit) {
            blitFramebuffer(prevFbo, mainFbo, w, h);
        }

        RenderCompat.withIrisBypass(() -> chain.process(target, resourceAllocator));

        if (needsBlit) {
            blitFramebuffer(mainFbo, prevFbo, w, h);
        }

        restoreGlState(prevFbo, prevViewport, prevDepthTest, prevBlend, prevScissor);
    }

    public void addToFrame(FrameGraphBuilder frameGraphBuilder, int width, int height,
                           PostChain.TargetBundle targetBundle) {
        PostChain chain = this.get();
        if (chain != null) {
            chain.addToFrame(frameGraphBuilder, width, height, targetBundle);
        }
    }

    private static void blitFramebuffer(int srcFbo, int dstFbo, int width, int height) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dstFbo);
        GL30.glBlitFramebuffer(0, 0, width, height, 0, 0, width, height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    private static void restoreGlState(int fbo, int[] viewport,
                                       boolean depthTest, boolean blend, boolean scissor) {
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        setGlToggle(GL11.GL_DEPTH_TEST, depthTest);
        setGlToggle(GL11.GL_BLEND, blend);
        setGlToggle(GL11.GL_SCISSOR_TEST, scissor);
    }

    private static void setGlToggle(int cap, boolean enabled) {
        if (enabled) GL11.glEnable(cap);
        else GL11.glDisable(cap);
    }
}
