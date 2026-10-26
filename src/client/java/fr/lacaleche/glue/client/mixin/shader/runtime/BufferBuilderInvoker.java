package fr.lacaleche.glue.client.mixin.shader.runtime;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BufferBuilder.class)
public interface BufferBuilderInvoker {
    @Invoker("beginElement")
    long glue$beginElement(VertexFormatElement element);
}
