package fr.lacaleche.glue.client.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RenderType.CompositeRenderType.class)
public interface CompositeRenderTypeAccessor {
    @Accessor("state")
    RenderType.CompositeState glue$getState();

    @Accessor("renderPipeline")
    RenderPipeline glue$getRenderPipeline();
}
