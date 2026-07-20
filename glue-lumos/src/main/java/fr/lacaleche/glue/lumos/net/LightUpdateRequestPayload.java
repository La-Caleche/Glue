package fr.lacaleche.glue.lumos.net;

import fr.lacaleche.glue.Glue;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client &rarr; server: request to replace the persistent light with this server-assigned id, keeping
 * the id. Gated like {@link LightAddRequestPayload}; a no-op if the id is unknown.
 */
public record LightUpdateRequestPayload(long id, Light light) implements CustomPacketPayload {

    public static final Type<LightUpdateRequestPayload> ID = new Type<>(Glue.id("lumos_light_update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightUpdateRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, LightUpdateRequestPayload::id,
                    LightCodecs.STREAM_CODEC, LightUpdateRequestPayload::light,
                    LightUpdateRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
