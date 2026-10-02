package fr.lacaleche.glue.client.shader.effect;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

import java.util.function.Function;

/**
 * Codec helpers for {@link TimedEffectDefinition}.
 *
 * <p>Maps human-readable curve names ({@code "linear"}, {@code "reverse"}, …) to
 * the corresponding {@code Function<Float, Float>} instances used by
 * {@link fr.lacaleche.glue.client.shader.TimedPostEffect.Builder}.</p>
 */
public final class TimedEffectCodecs {

    private TimedEffectCodecs() {
    }

    /**
     * Named progress curve types that can be expressed declaratively in JSON.
     *
     * <p>Custom curves with arbitrary lambdas still require Java code — only
     * the built-in set is data-driven.</p>
     */
    public enum CurveKey implements StringRepresentable {
        LINEAR("linear", t -> t),
        REVERSE("reverse", t -> 1.0f - t),
        EASE_IN("ease_in", t -> t * t),
        EASE_OUT("ease_out", t -> 1.0f - (1.0f - t) * (1.0f - t)),
        EASE_IN_OUT("ease_in_out", t -> t < 0.5f ? 2f * t * t : 1f - (float) Math.pow(-2f * t + 2f, 2) / 2f);

        public static final Codec<CurveKey> CODEC = StringRepresentable.fromEnum(CurveKey::values);

        private final String name;
        private final Function<Float, Float> curve;

        CurveKey(String name, Function<Float, Float> curve) {
            this.name = name;
            this.curve = curve;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Function<Float, Float> resolve() {
            return curve;
        }
    }
}
