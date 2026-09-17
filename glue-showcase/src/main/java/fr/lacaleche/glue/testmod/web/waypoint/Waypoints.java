package fr.lacaleche.glue.testmod.web.waypoint;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Waypoints of the current connection; they are forgotten on disconnect. Client thread only. */
public final class Waypoints {

    static final List<String> COLORS = List.of("#d6453d", "#e0a526", "#58a64f", "#3f8ed8", "#a070d8");
    private static final int LIMIT = 32;
    private static final int NAME_LENGTH = 24;
    private static final List<Waypoint> ALL = new ArrayList<>();
    private static int nextId = 1;

    private Waypoints() {
    }

    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ALL::clear));
    }

    static Waypoint create(String name, String color) {
        String label = name == null ? "" : name.strip();
        if (label.isEmpty() || label.length() > NAME_LENGTH) {
            throw new IllegalArgumentException("Name the waypoint with 1 to " + NAME_LENGTH + " characters");
        }
        if (!COLORS.contains(color)) throw new IllegalArgumentException("Pick one of the offered colors");
        if (ALL.size() >= LIMIT) throw new IllegalStateException("Remove a waypoint before adding another");

        LocalPlayer player = requirePlayer();
        Waypoint waypoint = new Waypoint(nextId++, label, color, player.level().dimension(), player.blockPosition());
        ALL.add(waypoint);
        return waypoint;
    }

    /** A camp at the player's position with the next free number. */
    public static Waypoint createCamp() {
        int number = 1;
        while (named("Camp " + number)) number++;
        return create("Camp " + number, COLORS.get((number - 1) % COLORS.size()));
    }

    static boolean remove(int id) {
        return ALL.removeIf(waypoint -> waypoint.id() == id);
    }

    static Optional<Waypoint> find(int id) {
        return ALL.stream().filter(waypoint -> waypoint.id() == id).findFirst();
    }

    public static List<Waypoint> all() {
        return List.copyOf(ALL);
    }

    static List<View> views() {
        LocalPlayer player = Minecraft.getInstance().player;
        return ALL.stream().map(waypoint -> waypoint.view(player)).toList();
    }

    private static boolean named(String name) {
        return ALL.stream().anyMatch(waypoint -> waypoint.name().equalsIgnoreCase(name));
    }

    private static LocalPlayer requirePlayer() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) throw new IllegalStateException("Join a world to place waypoints");
        return player;
    }

    public record Waypoint(int id, String name, String color, ResourceKey<Level> dimension, BlockPos position) {

        View view(LocalPlayer player) {
            boolean nearby = player != null && player.level().dimension().equals(this.dimension);
            int distance = nearby ? (int) Math.round(Math.sqrt(player.blockPosition().distSqr(this.position))) : -1;
            return new View(this.id, this.name, this.color, this.dimension.location().getPath(),
                    this.position.getX(), this.position.getY(), this.position.getZ(), distance);
        }
    }

    /** What pages receive; distance is -1 in another dimension. */
    record View(int id, String name, String color, String dimension, int x, int y, int z, int distance) {
    }
}
