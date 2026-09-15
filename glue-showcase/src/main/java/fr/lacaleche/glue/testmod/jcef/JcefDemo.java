package fr.lacaleche.glue.testmod.jcef;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.web.WebSurface;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class JcefDemo {

    private static LocalPages pages;
    private static WebSurface hud;
    private static int tick;
    private static int commands;

    private JcefDemo() {
    }

    public static void init() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("jcef")
                .then(literal("demo").executes(context -> { context.getSource().getClient().schedule(() -> open(context.getSource().getClient(), false)); return 1; }))
                .then(literal("browser").executes(context -> { context.getSource().getClient().schedule(() -> open(context.getSource().getClient(), true)); return 1; }))
                .then(literal("hud").executes(context -> { context.getSource().getClient().schedule(() -> openHud(context.getSource().getClient())); return 1; }))
                .then(literal("close").executes(context -> { closeHud(); return 1; }))));
        HudElementRegistry.addLast(TestmodClient.id("jcef"), (graphics, delta) -> {
            Minecraft client = Minecraft.getInstance();
            if (hud == null || hud.isClosed() || client.screen != null || client.level == null) return;
            hud.resize(Math.clamp(graphics.guiWidth() * 2, 1, 4096), Math.clamp(graphics.guiHeight() * 2, 1, 4096));
            hud.draw(graphics, 0, 0, graphics.guiWidth(), graphics.guiHeight());
            CefScreen.drawMetrics(graphics, hud, graphics.guiHeight() - 14, graphics.guiWidth(), false);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null && hud != null) closeHud();
            WebSurface surface = client.screen instanceof CefScreen screen ? screen.surface() : hud;
            if (surface == null || surface.isClosed()) return;
            if (++tick % 10 != 0) return;
            JsonObject state = new JsonObject();
            state.addProperty("fps", client.getFps());
            state.addProperty("position", client.player == null ? "Main menu" : String.format(Locale.ROOT,
                    "X %.1f · Y %.1f · Z %.1f", client.player.getX(), client.player.getY(), client.player.getZ()));
            surface.postMessage(state.toString());
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            closeHud();
            if (client.screen instanceof CefScreen screen) screen.closeSurface();
            if (pages != null) pages.close();
        });
        JcefGameTest.register();
    }

    public static CefScreen open(Minecraft client, boolean browse) {
        closeHud();
        LocalPages server = pages();
        CefScreen screen = new CefScreen(browse ? "https://lacaleche.cc/" : server.url(), server.url(), server.origin(), client.screen);
        client.setScreen(screen);
        return screen;
    }

    public static void openHud(Minecraft client) {
        closeHud();
        LocalPages server = pages();
        hud = WebSurface.builder(URI.create(server.url()))
                .size(Math.min(4096, client.getWindow().getGuiScaledWidth() * 2),
                        Math.min(4096, client.getWindow().getGuiScaledHeight() * 2))
                .onMessage(URI.create(server.origin()), JcefDemo::receiveMessage)
                .open();
        client.setScreen(null);
    }

    public static WebSurface hud() { return hud; }
    public static int receivedCommands() { return commands; }

    public static void closeHud() {
        if (hud != null) { hud.close(); hud = null; }
    }

    static void receiveMessage(String payload) {
        JsonObject message = JsonParser.parseString(payload).getAsJsonObject();
        commands++;
        Minecraft client = Minecraft.getInstance();
        if (message.has("action") && message.get("action").getAsString().equals("greet") && client.player != null) {
            client.player.displayClientMessage(Component.literal("Hello from Chromium, " + message.get("name").getAsString()), true);
        }
    }

    private static LocalPages pages() {
        if (pages == null) {
            try {
                pages = LocalPages.start(Minecraft.getInstance().gameDirectory.toPath()
                        .resolve(System.getProperty("glue.showcase.webRoot", "../web-demo/dist")));
            }
            catch (IOException exception) { throw new IllegalStateException("Could not start the local JCEF fixture", exception); }
        }
        return pages;
    }
}
