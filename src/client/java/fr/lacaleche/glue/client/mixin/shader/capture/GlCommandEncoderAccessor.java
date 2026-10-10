package fr.lacaleche.glue.client.mixin.shader.capture;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets raw GL code tell Blaze3D that the program and pipeline state it last applied are gone. */
@Mixin(GlCommandEncoder.class)
public interface GlCommandEncoderAccessor {

    @Accessor("lastProgram")
    void glue$setLastProgram(GlProgram program);

    @Accessor("lastPipeline")
    void glue$setLastPipeline(RenderPipeline pipeline);
}
