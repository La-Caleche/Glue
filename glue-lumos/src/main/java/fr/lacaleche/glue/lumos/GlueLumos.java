package fr.lacaleche.glue.lumos;

import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Common initializer for the shared light model. Registers the persistent-light payload types on both
 * sides so the codec is known wherever a packet is sent or received; the server handlers and client
 * receiver are wired by their respective modules.
 */
public final class GlueLumos implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(LightSyncPayload.ID, LightSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LightAddRequestPayload.ID, LightAddRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LightRemoveRequestPayload.ID, LightRemoveRequestPayload.STREAM_CODEC);
    }
}
