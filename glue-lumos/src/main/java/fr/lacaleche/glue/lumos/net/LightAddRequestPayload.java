package fr.lacaleche.glue.lumos.net;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client &rarr; server: request to add a persistent light to the sender's current dimension. Refused
 * unless the server opened the request channel and its policy accepts the sender (see
 * {@code PersistentLights.allowClientRequests}); when accepted the server assigns an id, persists the
 * light, and broadcasts the new set. The requested light is advisory until the server accepts it.
 */
public record LightAddRequestPayload(Light light) implements CustomPacketPayload {

    public static final Type<LightAddRequestPayload> ID = new Type<>(Glue.id("lumos_light_add"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightAddRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    LightCodecs.STREAM_CODEC, LightAddRequestPayload::light,
                    LightAddRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
