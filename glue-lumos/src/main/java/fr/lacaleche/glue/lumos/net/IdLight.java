package fr.lacaleche.glue.lumos.net;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A persistent light paired with the server-assigned id that identifies it for removal. */
public record IdLight(long id, Light light) {

    public static final StreamCodec<RegistryFriendlyByteBuf, IdLight> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, IdLight::id,
            LightCodecs.STREAM_CODEC, IdLight::light,
            IdLight::new);
}
