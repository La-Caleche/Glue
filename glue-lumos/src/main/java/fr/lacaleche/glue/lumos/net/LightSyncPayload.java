package fr.lacaleche.glue.lumos.net;

import fr.lacaleche.glue.Glue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * Server &rarr; client: the full set of persistent lights for the dimension the player is in. Sent on
 * join and dimension change, and again after any add or removal &mdash; the client replaces its
 * persistent set with this list, so it is always the authoritative snapshot.
 */
public record LightSyncPayload(List<IdLight> lights) implements CustomPacketPayload {

    public static final Type<LightSyncPayload> ID = new Type<>(Glue.id("lumos_light_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, LightSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    IdLight.STREAM_CODEC.apply(ByteBufCodecs.list()), LightSyncPayload::lights,
                    LightSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
