package fr.lacaleche.glue.lumos.net;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A world light paired with the server-assigned id that identifies it for removal. */
public record PlacedLight(long id, Light light) {

    public static final Codec<PlacedLight> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("id").forGetter(PlacedLight::id),
            LightCodecs.CODEC.fieldOf("light").forGetter(PlacedLight::light)
    ).apply(instance, PlacedLight::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlacedLight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PlacedLight::id,
            LightCodecs.STREAM_CODEC, PlacedLight::light,
            PlacedLight::new);
}
