package fr.lacaleche.glue.lumos.server;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import fr.lacaleche.glue.lumos.net.LightUpdateRequestPayload;
import fr.lacaleche.glue.lumos.net.PlacedLight;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * The server-side store behind {@link Lumos#place}: world lights held per dimension in the save
 * ({@link PersistentLightState}) and synced to the players there. Mods normally go through
 * {@link Lumos}; this class is where the storage, the limits, and the client-request gate live.
 *
 * <p><b>Clients cannot write here by default.</b> Lumos ships a client &rarr; server request channel so
 * client-driven tooling can place a world light, but an open channel means any connected client can
 * write to the save, so requests are refused unless the server opts in with
 * {@link #allowClientRequests}. Even then every request is bounded: the light must be well formed, near
 * the requesting player, and within the dimension's light limit.</p>
 */
public final class PersistentLights {

    /** Cap on stored lights per dimension, so a runaway placer cannot grow the save without bound. */
    public static final int MAX_LIGHTS_PER_DIMENSION = 4096;

    /** How far from a player a light may be placed or removed by that player's request, in blocks. */
    public static final double MAX_REQUEST_DISTANCE = 64.0;

    /** Refuses every client request. The default. */
    public static final ClientRequestPolicy DENY = player -> false;

    /**
     * Allows operators at permission level {@link Commands#LEVEL_OWNERS} (4): what {@code /op} grants
     * under the default {@code op-permission-level}, and what the world owner has in singleplayer or
     * LAN with cheats enabled. Deliberately stricter than vanilla's game-master blocks (level 2),
     * because an accepted request writes to the world save.
     */
    public static final ClientRequestPolicy OPERATORS = player ->
            player.createCommandSourceStack().hasPermission(Commands.LEVEL_OWNERS);

    private static volatile ClientRequestPolicy clientRequests = DENY;

    private PersistentLights() {
    }

    /** Decides which players, if any, may place and remove world lights from the client. */
    @FunctionalInterface
    public interface ClientRequestPolicy {
        boolean allows(ServerPlayer player);
    }

    /**
     * Opens the client request channel to the players this policy accepts. Off by default; pass
     * {@link #OPERATORS} for the usual creative-tooling case, or {@link #DENY} to close it again.
     * Server-side logic calling {@link Lumos#place} directly is never affected by this.
     */
    public static void allowClientRequests(ClientRequestPolicy policy) {
        clientRequests = policy;
    }

    /**
     * Adds a world light to the level's dimension, persists it, and syncs the new set to every player
     * there. Returns its id, or {@link Lumos#NO_LIGHT} if refused: a {@link LightType#GOBO}, whose mask
     * is a client texture that cannot be persisted or sent; a light that is not
     * {@link Light#isWellFormed() well formed}; or a dimension already holding
     * {@link #MAX_LIGHTS_PER_DIMENSION} lights.
     */
    public static long add(ServerLevel level, Light light) {
        if (light.type == LightType.GOBO || !light.isWellFormed()) return Lumos.NO_LIGHT;
        PersistentLightState state = state(level);
        if (state.size() >= MAX_LIGHTS_PER_DIMENSION) return Lumos.NO_LIGHT;
        long id = state.add(light);
        broadcast(level);
        return id;
    }

    /**
     * Replaces the world light with this id, keeping the id, persists it and syncs the new set.
     * Returns {@code false} if the id is unknown or the replacement is refused on the same grounds
     * as {@link #add}.
     */
    public static boolean update(ServerLevel level, long id, Light light) {
        if (light.type == LightType.GOBO || !light.isWellFormed()) return false;
        if (!state(level).update(id, light)) return false;
        broadcast(level);
        return true;
    }

    /** Removes a world light by its id; returns whether it existed. Syncs the new set on success. */
    public static boolean remove(ServerLevel level, long id) {
        if (!state(level).remove(id)) return false;
        broadcast(level);
        return true;
    }

    /** The world lights of a dimension, as sent to clients. */
    public static List<PlacedLight> all(ServerLevel level) {
        return state(level).entries();
    }

    /**
     * Wires the join/dimension-change sync and the client request handlers. Called once from the common
     * initializer; the events and handlers only fire where a server actually runs.
     */
    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> syncTo(player));

        ServerPlayNetworking.registerGlobalReceiver(LightAddRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                Light light = payload.light();
                if (clientRequests.allows(player) && isNear(player, light.x, light.y, light.z)) {
                    add(player.level(), light);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(LightUpdateRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                Light light = payload.light();
                if (clientRequests.allows(player) && isNear(player, light.x, light.y, light.z)) {
                    updateNearby(player, payload.id(), light);
                }
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(LightRemoveRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                if (clientRequests.allows(player)) removeNearby(player, payload.id());
            });
        });
    }

    /** A request may only replace a light the requesting player is standing near. */
    private static void updateNearby(ServerPlayer player, long id, Light light) {
        ServerLevel level = player.level();
        Light existing = state(level).get(id);
        if (existing != null && isNear(player, existing.x, existing.y, existing.z)) {
            update(level, id, light);
        }
    }

    /** A request may only remove a light the requesting player is standing near. */
    private static void removeNearby(ServerPlayer player, long id) {
        ServerLevel level = player.level();
        Light light = state(level).get(id);
        if (light != null && isNear(player, light.x, light.y, light.z)) remove(level, id);
    }

    private static boolean isNear(ServerPlayer player, double x, double y, double z) {
        return player.distanceToSqr(x, y, z) <= MAX_REQUEST_DISTANCE * MAX_REQUEST_DISTANCE;
    }

    private static void syncTo(ServerPlayer player) {
        ServerPlayNetworking.send(player, new LightSyncPayload(all(player.level())));
    }

    private static void broadcast(ServerLevel level) {
        LightSyncPayload payload = new LightSyncPayload(all(level));
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static PersistentLightState state(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PersistentLightState.TYPE);
    }
}
