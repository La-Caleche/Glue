package fr.lacaleche.glue.client.mixin.shader.runtime;

import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Wraps a program Glue linked itself, as {@code GlProgram.link} would once the driver is done. */
@Mixin(GlProgram.class)
public interface GlProgramInvoker {
    @Invoker("<init>")
    static GlProgram glue$create(int programId, String debugLabel) {
        throw new AssertionError();
    }
}
