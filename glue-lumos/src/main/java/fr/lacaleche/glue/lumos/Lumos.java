package fr.lacaleche.glue.lumos;

import fr.lacaleche.glue.lumos.net.PlacedLight;
import fr.lacaleche.glue.lumos.server.PersistentLights;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The way to put light in the world. One entry point, callable from anywhere on either side: pass the
 * {@link Level} you are working with and Lumos does the side-correct thing, the same way {@code setBlock}
 * and {@code addParticle} do.
 *
 * <p>The only distinction that matters is the one you would make for a block:</p>
 * <ul>
 *   <li>{@link #place} &mdash; the <b>world</b> owns this light. It is saved with the dimension, synced
 *       to everyone in it, and comes back on reload. Called on the server it is placed directly; called
 *       on the client it asks the server, which may refuse (see {@link PersistentLights}).</li>
 *   <li>{@link #spawn} and {@link #attach} &mdash; a <b>visual</b> light, the light equivalent of a
 *       particle: seen by the local client, never saved, never sent, gone with the session. Harmless
 *       no-ops on a server.</li>
 * </ul>
 *
 * <p>Nothing here needs a side check at the call site, and nothing here needs to know whether it is in
 * a client mod, a server mod, or shared code.</p>
 */
public final class Lumos {

    /** Returned by {@link #place} when no light was placed, and when the client asked the server. */
    public static final long NO_LIGHT = -1L;

    @Nullable
    private static LumosClientBridge clientBridge;

    private Lumos() {
    }

    /**
     * Places a world light: saved with the dimension and synced to the players in it.
     *
     * <p>On the server this places it and returns the id identifying it for {@link #remove}, or
     * {@link #NO_LIGHT} if refused &mdash; a {@link LightType#GOBO}, whose mask is a client texture that
     * cannot be saved; a malformed light; or a dimension at its limit. On the client this sends a
     * request and returns {@link #NO_LIGHT}, because only the server assigns ids: watch
     * {@link #lights} for the result, and note the server refuses client requests unless it has opened
     * that channel.</p>
     */
    public static long place(Level level, Light light) {
        if (level instanceof ServerLevel server) return PersistentLights.add(server, light);
        if (clientBridge != null) clientBridge.requestPlace(light);
        return NO_LIGHT;
    }

    /**
     * Replaces the world light with this id, keeping the id &mdash; how tooling edits a placed light
     * without churning ids. On the server, returns whether a light with this id existed and the
     * replacement was accepted (refused on the same grounds as {@link #place}); on the client, sends a
     * request and returns {@code false}, the change arriving with the next sync.
     */
    public static boolean update(Level level, long id, Light light) {
        if (level instanceof ServerLevel server) return PersistentLights.update(server, id, light);
        if (clientBridge != null) clientBridge.requestUpdate(id, light);
        return false;
    }

    /**
     * Removes the world light with this id. On the server, returns whether it was there; on the client,
     * sends a request and returns {@code false}, the removal arriving with the next sync.
     */
    public static boolean remove(Level level, long id) {
        if (level instanceof ServerLevel server) return PersistentLights.remove(server, id);
        if (clientBridge != null) clientBridge.requestRemove(id);
        return false;
    }

    /** The world lights of this dimension, keyed by id: the stored set on the server, the synced one on the client. */
    public static Map<Long, Light> lights(Level level) {
        if (level instanceof ServerLevel server) {
            Map<Long, Light> result = new LinkedHashMap<>();
            for (PlacedLight entry : PersistentLights.all(server)) {
                result.put(entry.id(), entry.light());
            }
            return result;
        }
        return clientBridge == null ? Map.of() : clientBridge.placed();
    }

    /**
     * Spawns a visual light for the local client. Returns the light, which identifies it for
     * {@link #despawn}. A no-op on the server, and outside a client world.
     */
    public static Light spawn(Level level, Light light) {
        if (level.isClientSide && clientBridge != null) clientBridge.spawn(light);
        return light;
    }

    /** Removes a visual light spawned by {@link #spawn}. */
    public static void despawn(Level level, Light light) {
        if (level.isClientSide && clientBridge != null) clientBridge.despawn(light);
    }

    /**
     * Spawns a visual light that follows something that moves, re-sampling its transform once per
     * rendered frame rather than once per tick. Returns the handle that repositions and removes it, or
     * {@code null} on the server, where there is nothing to render.
     */
    @Nullable
    public static LightHandle attach(Level level, Light light, LightAttachment attachment) {
        if (!level.isClientSide || clientBridge == null) return null;
        return clientBridge.attach(light, attachment);
    }

    /** Every light the renderer will draw, visual and world alike. Empty on the server. */
    public static List<Light> active(Level level) {
        if (!level.isClientSide || clientBridge == null) return List.of();
        return clientBridge.active();
    }

    /**
     * Connects the client renderer to the visual-light calls. Called by the Lumos client initializer;
     * a server never installs one, which is what makes those calls no-ops there.
     */
    public static void installClientBridge(LumosClientBridge bridge) {
        clientBridge = bridge;
    }
}
