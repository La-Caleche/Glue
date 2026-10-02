package fr.lacaleche.glue.client.render.outline;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.math.Color;
import net.minecraft.util.StringRepresentable;

/**
 * Data-driven description of an outline renderer, loaded from
 * {@code assets/<modid>/glue/outlines/<name>.json}.
 *
 * <p>Uses a {@code type} discriminator so future outline styles (dashed,
 * animated, glowing, …) can be added without breaking existing definitions.
 * The current built-in type is {@link Type#SIMPLE}.</p>
 *
 * <h3>Simple outline</h3>
 * <pre>{@code
 * {
 *   "type": "simple",
 *   "red": 255,
 *   "green": 0,
 *   "blue": 0,
 *   "alpha": 0.4
 * }
 * }</pre>
 */
public record OutlineDefinition(Type type, int red, int green, int blue, float alpha) {

    private static final int DEFAULT_RED = 0;
    private static final int DEFAULT_GREEN = 0;
    private static final int DEFAULT_BLUE = 0;
    private static final float DEFAULT_ALPHA = 0.4f;

    /**
     * Outline type discriminator. New types can be added here — each provides
     * its own {@link MapCodec} and a factory that turns the parsed definition
     * into a concrete {@link GlueOutlineRenderer}.
     */
    public enum Type implements StringRepresentable {
        SIMPLE("simple");

        public static final Codec<Type> CODEC = StringRepresentable.fromEnum(Type::values);

        private final String name;

        Type(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public MapCodec<OutlineDefinition> mapCodec() {
            return switch (this) {
                case SIMPLE -> RecordCodecBuilder.mapCodec(i -> i.group(
                        Codec.INT.optionalFieldOf("red", DEFAULT_RED).forGetter(OutlineDefinition::red),
                        Codec.INT.optionalFieldOf("green", DEFAULT_GREEN).forGetter(OutlineDefinition::green),
                        Codec.INT.optionalFieldOf("blue", DEFAULT_BLUE).forGetter(OutlineDefinition::blue),
                        Codec.FLOAT.optionalFieldOf("alpha", DEFAULT_ALPHA).forGetter(OutlineDefinition::alpha)
                ).apply(i, (r, g, b, a) -> new OutlineDefinition(Type.SIMPLE, r, g, b, a)));
            };
        }

        public GlueOutlineRenderer bake(OutlineDefinition def) {
            return switch (this) {
                case SIMPLE -> new ParametricOutlineRenderer(
                        Color.ofRGB(def.red, def.green, def.blue), def.alpha);
            };
        }
    }

    /**
     * Dispatching codec: reads {@code "type"}, then delegates to the
     * type-specific {@link MapCodec} for the remaining fields.
     */
    public static final Codec<OutlineDefinition> CODEC = Type.CODEC.dispatch(
            "type", OutlineDefinition::type, Type::mapCodec);

    /**
     * Materializes this definition into a {@link GlueOutlineRenderer}
     * using the type-specific factory.
     */
    public GlueOutlineRenderer bake() {
        return type.bake(this);
    }
}
