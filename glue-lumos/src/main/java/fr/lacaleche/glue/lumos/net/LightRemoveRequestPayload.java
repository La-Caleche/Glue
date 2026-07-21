package fr.lacaleche.glue.lumos.net;

import fr.lacaleche.glue.Glue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client &rarr; server: request to remove the persistent light with this server-assigned id from the
 * sender's current dimension. Gated like {@link LightAddRequestPayload}; a no-op if the id is unknown.
 */
public record LightRemoveRequestPayload(long id) implements CustomPacketPayload {

    public static final Type<LightRemoveRequestPayload> ID = new Type<>(Glue.id("lumos_light_remove"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightRemoveRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, LightRemoveRequestPayload::id,
                    LightRemoveRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
