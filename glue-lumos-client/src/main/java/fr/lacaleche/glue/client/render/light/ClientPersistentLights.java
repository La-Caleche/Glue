package fr.lacaleche.glue.client.render.light;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.net.IdLight;
import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client mirror of the server's persistent lights. Receives the authoritative set for the current
 * dimension and feeds it to the {@link LightManager} for rendering, and requests additions/removals
 * from the server (which validates them and, on success, syncs the new set back &mdash; the client
 * never decides what is persistent, it only reflects and requests).
 */
@Environment(EnvType.CLIENT)
public final class ClientPersistentLights {

    private static final Map<Long, Light> tracked = new LinkedHashMap<>();
    private static List<IdLight> desired = List.of();

    private ClientPersistentLights() {
    }

    /** Registers the sync receiver. Called from the client initializer. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(LightSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> apply(payload.lights())));
    }

    /** Asks the server to add this light as persistent; it appears once the server accepts and syncs. */
    public static void requestAdd(Light light) {
        ClientPlayNetworking.send(new LightAddRequestPayload(light));
    }

    /** Asks the server to remove the persistent light with this server-assigned id. */
    public static void requestRemove(long id) {
        ClientPlayNetworking.send(new LightRemoveRequestPayload(id));
    }

    /** The persistent lights currently shown, keyed by their server id. */
    public static Map<Long, Light> current() {
        return Collections.unmodifiableMap(tracked);
    }

    /**
     * Re-homes the persistent set into the new world's light context. The server resyncs the correct
     * set on a dimension change, so a briefly-carried set is corrected within a tick; leaving a world
     * ({@code level == null}) drops it entirely.
     */
    public static void onWorldSwitch(@Nullable ClientLevel level) {
        if (level == null) desired = List.of();
        reconcile();
    }

    private static void apply(List<IdLight> lights) {
        desired = lights;
        reconcile();
    }

    private static void reconcile() {
        LightManager manager = LightManager.getInstance();
        tracked.values().forEach(manager::remove);
        tracked.clear();
        if (manager.currentWorld() == null) return;
        for (IdLight entry : desired) {
            tracked.put(entry.id(), manager.add(entry.light()));
        }
    }
}
