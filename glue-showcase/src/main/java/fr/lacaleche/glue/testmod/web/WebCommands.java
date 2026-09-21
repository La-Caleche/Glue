package fr.lacaleche.glue.testmod.web;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import fr.lacaleche.glue.testmod.web.browser.BrowserScreen;
import fr.lacaleche.glue.web.host.WebHud;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * {@code /web} opens the demos and toggles the layers. Actions are scheduled because the chat
 * screen closes after the command runs and would otherwise replace a screen opened here.
 */
final class WebCommands {

    private WebCommands() {
    }

    static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(literal("web")
                .then(action("hub", WebDemos::openHub))
                .then(action("lab", WebDemos::openLab))
                .then(action("bundles", BundleDemo::open))
                .then(action("waypoints", WebDemos::openWaypoints))
                .then(action("browser", () -> BrowserScreen.open(BrowserScreen.HOME))
                        .then(argument("url", StringArgumentType.greedyString()).executes(context -> {
                            String url = StringArgumentType.getString(context, "url");
                            schedule(() -> BrowserScreen.open(url));
                            return 1;
                        })))
                .then(action("hud", () -> toggle(WebDemos.vitals())))
                .then(action("minimap", () -> toggle(WebDemos.minimap())))
                .then(literal("toast").then(argument("message", StringArgumentType.greedyString()).executes(context -> {
                    String message = StringArgumentType.getString(context, "message");
                    schedule(() -> WebDemos.toast("From the chat", message));
                    return 1;
                })))));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> action(String name, Runnable action) {
        return literal(name).executes(context -> {
            schedule(action);
            return 1;
        });
    }

    private static void toggle(WebHud hud) {
        hud.setEnabled(!hud.isEnabled());
    }

    private static void schedule(Runnable action) {
        Minecraft.getInstance().schedule(action);
    }
}
