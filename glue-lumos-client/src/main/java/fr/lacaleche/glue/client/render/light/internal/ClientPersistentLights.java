package fr.lacaleche.glue.client.render.light.internal;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.net.PlacedLight;
import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import fr.lacaleche.glue.lumos.net.LightUpdateRequestPayload;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Client mirror of the server's world lights, behind
 * {@link fr.lacaleche.glue.lumos.Lumos}. Receives the authoritative set for the
 * current dimension and feeds it to the {@link LightManager} for rendering, and forwards place/remove
 * requests to the server, which validates them and, on success, syncs the new set back &mdash; the
 * client never decides what is placed, it only reflects and asks.
 */
@Environment(EnvType.CLIENT)
public final class ClientPersistentLights {

    private static final Map<Long, Light> tracked = new LinkedHashMap<>();
    private static List<PlacedLight> desired = List.of();

    private ClientPersistentLights() {
    }

    /** Registers the sync receiver. Called from the client initializer. */
    public static void registerClient() {
        ClientPlayNetworking.registerGlobalReceiver(LightSyncPayload.ID,
                (payload, context) -> context.client().execute(() -> apply(payload.lights())));
    }

    /** Asks the server to place this light; it appears once the server accepts and syncs it back. */
    public static void requestPlace(Light light) {
        ClientPlayNetworking.send(new LightAddRequestPayload(light));
    }

    /** Asks the server to replace the persistent light with this id, keeping the id. */
    public static void requestUpdate(long id, Light light) {
        ClientPlayNetworking.send(new LightUpdateRequestPayload(id, light));
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
     * Re-homes the persistent set into the new world's light context: a full rebuild, since the old
     * context died with its world and took the shown instances with it. The server resyncs the correct
     * set on a dimension change, so a briefly-carried set is corrected within a tick; leaving a world
     * ({@code level == null}) drops it entirely.
     */
    public static void onWorldSwitch(@Nullable ClientLevel level) {
        LightManager manager = LightManager.getInstance();
        tracked.values().forEach(manager::remove);
        tracked.clear();
        if (level == null) {
            desired = List.of();
            return;
        }
        reconcile();
    }

    private static void apply(List<PlacedLight> lights) {
        desired = lights;
        reconcile();
    }

    /**
     * Diffs the synced snapshot against what is shown instead of rebuilding it: the shadow and glass
     * caches key on light identity, so an untouched light must keep its instance or a single add
     * elsewhere in the dimension would re-bake every world light. An id is never reused within a save,
     * but an update keeps its id, so same-id entries still compare by value.
     */
    private static void reconcile() {
        LightManager manager = LightManager.getInstance();
        if (manager.currentWorld() == null) {
            tracked.clear();
            return;
        }
        Map<Long, Light> incoming = new LinkedHashMap<>();
        for (PlacedLight entry : desired) {
            incoming.put(entry.id(), entry.light());
        }
        Iterator<Map.Entry<Long, Light>> shown = tracked.entrySet().iterator();
        while (shown.hasNext()) {
            Map.Entry<Long, Light> entry = shown.next();
            Light replacement = incoming.get(entry.getKey());
            if (replacement != null && sameLight(entry.getValue(), replacement)) {
                incoming.remove(entry.getKey());
            } else {
                manager.remove(entry.getValue());
                shown.remove();
            }
        }
        incoming.forEach((id, light) -> tracked.put(id, manager.add(light)));
    }

    /**
     * Field-by-field equality. {@link Light} deliberately has no {@code equals} &mdash; the renderer's
     * caches key on identity &mdash; so the diff compares values here.
     */
    private static boolean sameLight(Light a, Light b) {
        return a.type == b.type
                && a.x == b.x && a.y == b.y && a.z == b.z
                && a.directionX == b.directionX && a.directionY == b.directionY
                && a.directionZ == b.directionZ
                && a.r == b.r && a.g == b.g && a.b == b.b
                && a.intensity == b.intensity && a.range == b.range
                && a.cosInner == b.cosInner && a.cosOuter == b.cosOuter
                && a.castsShadow == b.castsShadow;
    }
}
