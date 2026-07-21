package fr.lacaleche.glue.mcsx.client.mixin;

import com.mojang.blaze3d.opengl.GlCommandEncoder;
import com.mojang.blaze3d.opengl.GlProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@code GlCommandEncoder} caches the last shader program it bound ({@code lastProgram}) and skips
 * {@code glUseProgram} when a draw requests the same one — unlike {@code lastPipeline}, the field
 * is never reset between render passes or frames. Any raw-GL code that binds its own programs
 * (Arc3D's flush, the dock present pass) leaves that cache pointing at a program that is no longer
 * bound; nulling it forces the next draw to re-bind for real.
 */
@Mixin(GlCommandEncoder.class)
public interface GlCommandEncoderAccessor {

    @Accessor("lastProgram")
    void mcsx$setLastProgram(GlProgram program);
}
