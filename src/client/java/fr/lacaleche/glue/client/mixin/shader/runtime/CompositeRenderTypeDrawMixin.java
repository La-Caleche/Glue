package fr.lacaleche.glue.client.mixin.shader.runtime;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.shader.internal.RenderPassBinders;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(RenderType.CompositeRenderType.class)
public class CompositeRenderTypeDrawMixin {

    /** The pass is only reachable inside {@code draw}; binding last lets a RenderType override vanilla's samplers. */
    @WrapOperation(method = "draw", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/RenderPass;setIndexBuffer(Lcom/mojang/blaze3d/buffers/GpuBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$IndexType;)V"))
    private void glue$bindRenderTypeUniforms(RenderPass pass, GpuBuffer indices, VertexFormat.IndexType type, Operation<Void> original) {
        RenderPassBinders.bind((RenderType) (Object) this, pass);
        original.call(pass, indices, type);
    }
}
