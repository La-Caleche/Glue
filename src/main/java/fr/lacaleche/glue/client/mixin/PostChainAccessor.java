package fr.lacaleche.glue.client.mixin;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * Accessor mixin for {@link PostChain} to expose its internal passes list.
 * Required for dynamic uniform updates on post-processing shaders at runtime.
 */
@Mixin(PostChain.class)
public interface PostChainAccessor {

    @Accessor("passes")
    List<PostPass> glue$getPasses();
}
