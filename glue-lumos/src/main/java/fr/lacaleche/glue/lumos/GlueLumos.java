package fr.lacaleche.glue.lumos;

import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import fr.lacaleche.glue.lumos.net.LightUpdateRequestPayload;
import fr.lacaleche.glue.lumos.server.PersistentLights;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Common initializer for the shared light model. Registers the persistent-light payload types on both
 * sides so the codec is known wherever a packet is sent or received, and wires the server-side
 * persistence (its events and handlers only fire where a server runs). The client receiver is wired by
 * the renderer module.
 */
public final class GlueLumos implements ModInitializer {

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(LightSyncPayload.ID, LightSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LightAddRequestPayload.ID, LightAddRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LightUpdateRequestPayload.ID, LightUpdateRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(LightRemoveRequestPayload.ID, LightRemoveRequestPayload.STREAM_CODEC);

        PersistentLights.registerServer();
    }
}
