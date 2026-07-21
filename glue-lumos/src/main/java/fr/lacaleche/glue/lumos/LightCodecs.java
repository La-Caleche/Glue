package fr.lacaleche.glue.lumos;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * Serialization for {@link Light}: a {@link Codec} for world-save NBT and a {@link StreamCodec} for the
 * network. Both round-trip every field except {@code goboTextureId} &mdash; a live client GL handle that
 * is meaningless on disk or on the server &mdash; so a decoded light always has no gobo mask. The
 * persistence layer rejects {@link LightType#GOBO} for that reason; the codecs still encode its
 * {@code type} faithfully so a mistaken gobo degrades to a plain cone rather than corrupting the stream.
 */
public final class LightCodecs {

    public static final Codec<LightType> TYPE = Codec.STRING.xmap(LightType::valueOf, LightType::name);

    public static final Codec<Light> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            TYPE.fieldOf("type").forGetter(light -> light.type),
            Codec.DOUBLE.fieldOf("x").forGetter(light -> light.x),
            Codec.DOUBLE.fieldOf("y").forGetter(light -> light.y),
            Codec.DOUBLE.fieldOf("z").forGetter(light -> light.z),
            Codec.FLOAT.fieldOf("dirX").forGetter(light -> light.directionX),
            Codec.FLOAT.fieldOf("dirY").forGetter(light -> light.directionY),
            Codec.FLOAT.fieldOf("dirZ").forGetter(light -> light.directionZ),
            Codec.FLOAT.fieldOf("r").forGetter(light -> light.r),
            Codec.FLOAT.fieldOf("g").forGetter(light -> light.g),
            Codec.FLOAT.fieldOf("b").forGetter(light -> light.b),
            Codec.FLOAT.fieldOf("intensity").forGetter(light -> light.intensity),
            Codec.FLOAT.fieldOf("range").forGetter(light -> light.range),
            Codec.FLOAT.fieldOf("cosInner").forGetter(light -> light.cosInner),
            Codec.FLOAT.fieldOf("cosOuter").forGetter(light -> light.cosOuter),
            Codec.BOOL.fieldOf("castsShadow").forGetter(light -> light.castsShadow)
    ).apply(instance, Light::raw));

    public static final StreamCodec<FriendlyByteBuf, Light> STREAM_CODEC = StreamCodec.of(
            (buffer, light) -> {
                buffer.writeEnum(light.type);
                buffer.writeDouble(light.x);
                buffer.writeDouble(light.y);
                buffer.writeDouble(light.z);
                buffer.writeFloat(light.directionX);
                buffer.writeFloat(light.directionY);
                buffer.writeFloat(light.directionZ);
                buffer.writeFloat(light.r);
                buffer.writeFloat(light.g);
                buffer.writeFloat(light.b);
                buffer.writeFloat(light.intensity);
                buffer.writeFloat(light.range);
                buffer.writeFloat(light.cosInner);
                buffer.writeFloat(light.cosOuter);
                buffer.writeBoolean(light.castsShadow);
            },
            buffer -> Light.raw(
                    buffer.readEnum(LightType.class),
                    buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(),
                    buffer.readFloat(), buffer.readFloat(),
                    buffer.readBoolean()));

    private LightCodecs() {
    }
}
