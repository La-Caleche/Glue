package fr.lacaleche.glue.client.mixin.shader.runtime;

import com.mojang.blaze3d.opengl.GlDevice;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(GlDevice.class)
public interface GlDeviceAccessor {
    @Accessor("pipelineCache")
    Map<RenderPipeline, GlRenderPipeline> glue$getPipelineCache();

    /** Keyed by the package-private {@code GlDevice.ShaderCompilationKey}, read through {@link ShaderCompilationKeyAccessor}. */
    @Accessor("shaderCache")
    Map<?, GlShaderModule> glue$getShaderCache();
}
