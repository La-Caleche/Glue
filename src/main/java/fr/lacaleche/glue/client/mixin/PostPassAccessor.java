package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * Accessor mixin for {@link PostPass} to expose its custom uniform buffers.
 * Required for dynamic uniform updates on post-processing shaders at runtime.
 */
@Mixin(PostPass.class)
public interface PostPassAccessor {

    @Accessor("customUniforms")
    Map<String, GpuBuffer> glue$getCustomUniforms();
}
