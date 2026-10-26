package fr.lacaleche.glue.client.mixin.shader.runtime;

import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "com.mojang.blaze3d.opengl.GlDevice$ShaderCompilationKey")
public interface ShaderCompilationKeyAccessor {
    @Accessor("id")
    ResourceLocation glue$getId();
}
