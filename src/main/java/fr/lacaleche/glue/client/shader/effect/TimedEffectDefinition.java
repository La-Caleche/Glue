package fr.lacaleche.glue.client.shader.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.TimedPostEffect;
import fr.lacaleche.glue.client.registries.GlueClientRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Data-driven description of a {@link TimedPostEffect}, loaded from
 * {@code assets/<modid>/glue/post_effects/<name>.json}.
 *
 * <p>{@link #bake()} resolves the referenced {@link PostShaderHandle} from the
 * client registry and delegates to {@link TimedPostEffect.Builder}.</p>
 *
 * <p><b>Limitation:</b> custom uniform writers and arbitrary curve lambdas
 * cannot be expressed in JSON. Effects requiring those must still be defined
 * in Java. This definition covers the common case: a named curve and
 * a simple {@code putProgress()} writer.</p>
 *
 * <pre>{@code
 * {
 *   "post_chain": "glue-test:blur",
 *   "ubo_name": "BlurConfig",
 *   "ubo_size": 4,
 *   "duration": 20,
 *   "curve": "reverse"
 * }
 * }</pre>
 */
public record TimedEffectDefinition(
        ResourceLocation postChain,
        String uboName,
        int uboSize,
        int duration,
        Optional<TimedEffectCodecs.CurveKey> curve
) {

    public static final Codec<TimedEffectDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC.fieldOf("post_chain").forGetter(TimedEffectDefinition::postChain),
            Codec.STRING.fieldOf("ubo_name").forGetter(TimedEffectDefinition::uboName),
            Codec.INT.optionalFieldOf("ubo_size", 4).forGetter(TimedEffectDefinition::uboSize),
            Codec.INT.optionalFieldOf("duration", 20).forGetter(TimedEffectDefinition::duration),
            TimedEffectCodecs.CurveKey.CODEC.optionalFieldOf("curve").forGetter(TimedEffectDefinition::curve)
    ).apply(i, TimedEffectDefinition::new));

    /**
     * Materializes this definition into a {@link TimedPostEffect}.
     *
     * @throws IllegalStateException if the referenced post chain is not registered
     */
    public TimedPostEffect bake() {
        PostShaderHandle handle = GlueClientRegistries.POST_CHAINS.getValue(postChain);
        if (handle == null) {
            throw new IllegalStateException(
                    "TimedEffectDefinition references unknown post chain: " + postChain);
        }

        TimedPostEffect.Builder builder = TimedPostEffect.builder(handle)
                .ubo(uboName, uboSize)
                .duration(duration);

        curve.ifPresent(key -> builder.curve(key.resolve()));

        return builder.build();
    }
}
