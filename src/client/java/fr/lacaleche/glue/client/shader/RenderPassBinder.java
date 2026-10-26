package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.systems.RenderPass;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Binds what one {@link net.minecraft.client.renderer.RenderType} adds to its pipeline's defaults, uniform
 * blocks and samplers, on the pass that draws it, after vanilla's uniforms and {@code Sampler0}-{@code 11},
 * so it may replace them. The caller owns the buffers and textures it binds.
 */
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface RenderPassBinder {

    void bind(RenderPass pass);
}
