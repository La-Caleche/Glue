package fr.lacaleche.glue.lumos.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A persistent light paired with the server-assigned id that identifies it for removal. */
public record IdLight(long id, Light light) {

    public static final Codec<IdLight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(IdLight::id),
            LightCodecs.CODEC.fieldOf("light").forGetter(IdLight::light)
    ).apply(instance, IdLight::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, IdLight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, IdLight::id,
            LightCodecs.STREAM_CODEC, IdLight::light,
            IdLight::new);
}
