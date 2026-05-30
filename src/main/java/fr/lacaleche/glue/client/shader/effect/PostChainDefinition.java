package fr.lacaleche.glue.client.shader.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Data-driven description of a {@link PostShaderHandle}, loaded from
 * {@code assets/<modid>/glue/post_chains/<name>.json}.
 */
public record PostChainDefinition(
        Optional<List<ResourceLocation>> externalTargets
) {

    public static final Codec<PostChainDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.listOf()
                    .optionalFieldOf("external_targets")
                    .forGetter(PostChainDefinition::externalTargets)
    ).apply(i, PostChainDefinition::new));

    public PostShaderHandle bake(ResourceLocation id) {
        Set<ResourceLocation> targets = externalTargets
                .map(Set::copyOf)
                .orElse(LevelTargetBundle.MAIN_TARGETS);
        return new PostShaderHandle(id, targets);
    }
}
