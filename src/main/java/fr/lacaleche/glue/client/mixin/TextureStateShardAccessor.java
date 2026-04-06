package fr.lacaleche.glue.client.mixin;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;

/**
 * Mixin invoker for {@link RenderStateShard.EmptyTextureStateShard#cutoutTexture()},
 * which is protected. Exposes it so we can extract the texture ResourceLocation.
 */
@Mixin(RenderStateShard.EmptyTextureStateShard.class)
public interface TextureStateShardAccessor {
    @Invoker("cutoutTexture")
    Optional<ResourceLocation> glue$getCutoutTexture();
}
