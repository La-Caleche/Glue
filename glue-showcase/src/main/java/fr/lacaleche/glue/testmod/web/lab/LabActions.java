package fr.lacaleche.glue.testmod.web.lab;

import fr.lacaleche.glue.web.bridge.WebAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Actions and state of the input lab page. */
public final class LabActions {

    private static int increments;

    /** Increments across every lab screen, so scripted tests can observe page-to-Java calls. */
    public static int increments() {
        return increments;
    }

    public static Game snapshot() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        String position = player == null ? "Main menu" : String.format(Locale.ROOT, "%d, %d, %d",
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        return new Game(client.getFps(), position);
    }

    @WebAction("lab.increment")
    int increment() {
        return ++increments;
    }

    @WebAction("lab.greet")
    String greet(Greeting greeting) {
        String name = greeting == null || greeting.name() == null ? "" : greeting.name().strip();
        if (name.isEmpty()) throw new IllegalArgumentException("Type a callsign first");
        String message = "Hello from Chromium, " + name;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(message), true);
        return message;
    }

    @WebAction("lab.refuse")
    void refuse() {
        throw new IllegalStateException("The lab refuses this request on purpose");
    }

    record Greeting(String name) {
    }

    public record Game(int fps, String position) {
    }
}
