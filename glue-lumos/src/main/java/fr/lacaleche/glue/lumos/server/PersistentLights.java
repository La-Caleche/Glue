package fr.lacaleche.glue.lumos.server;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import fr.lacaleche.glue.lumos.net.IdLight;
import fr.lacaleche.glue.lumos.net.LightAddRequestPayload;
import fr.lacaleche.glue.lumos.net.LightRemoveRequestPayload;
import fr.lacaleche.glue.lumos.net.LightSyncPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Server-authoritative persistent lights. Consumers add and remove them here from server-side logic;
 * the state lives per dimension in the world save ({@link PersistentLightState}) and is synced to the
 * clients in that dimension so the renderer can draw it. Runs on the logical server &mdash; the
 * integrated server in singleplayer, the dedicated server in multiplayer.
 */
public final class PersistentLights {

    private PersistentLights() {
    }

    /**
     * Adds a persistent light to the level's dimension, persists it, and syncs the new set to every
     * player there. Returns the id that identifies it for {@link #remove}, or {@code -1} if rejected:
     * a {@link LightType#GOBO}, whose mask is a client GL texture that cannot be persisted or sent.
     */
    public static long add(ServerLevel level, Light light) {
        if (light.type == LightType.GOBO) return -1L;
        long id = state(level).add(light);
        broadcast(level);
        return id;
    }

    /** Removes a persistent light by its id; returns whether it existed. Syncs the new set on success. */
    public static boolean remove(ServerLevel level, long id) {
        if (!state(level).remove(id)) return false;
        broadcast(level);
        return true;
    }

    /** The persistent lights of a dimension, as sent to clients. */
    public static List<IdLight> all(ServerLevel level) {
        return state(level).entries();
    }

    /**
     * Wires the join/dimension-change sync and the gated client-request handlers. Called once from the
     * common initializer; the events and handlers only fire where a server actually runs.
     */
    public static void registerServer() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> syncTo(handler.player));
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
                (player, origin, destination) -> syncTo(player));

        ServerPlayNetworking.registerGlobalReceiver(LightAddRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                if (mayModify(player)) add(player.level(), payload.light());
            });
        });
        ServerPlayNetworking.registerGlobalReceiver(LightRemoveRequestPayload.ID, (payload, context) -> {
            ServerPlayer player = context.player();
            player.getServer().execute(() -> {
                if (mayModify(player)) remove(player.level(), payload.id());
            });
        });
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

    /** Singleplayer and LAN (integrated server) trust the client; a dedicated server requires an op. */
    private static boolean mayModify(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        return !server.isDedicatedServer() || player.createCommandSourceStack().hasPermission(2);
    }
}
