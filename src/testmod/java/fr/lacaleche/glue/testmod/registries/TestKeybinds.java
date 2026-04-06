package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Function;

public class TestKeybinds {

    public static final KeybindingsRegistry KEYBINDINGS = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final KeyMapping TOGGLE_RAYCAST_DEBUG = KEYBINDINGS.register(
            "toggle_raycast_debug",
            "key.categories.glue_test", GLFW.GLFW_KEY_R, client -> TestmodClient.getInstance().toggleRaycastDebug());

    public static final KeyMapping TOGGLE_BLUR = KEYBINDINGS.register(
            "toggle_blur",
            "key.categories.glue_test", GLFW.GLFW_KEY_B,
            toggleWithMessage("Blur post-effect", client -> TestPostShaderHandler.toggleBlur()));

    public static final KeyMapping TOGGLE_GRAYSCALE = KEYBINDINGS.register(
            "toggle_grayscale",
            "key.categories.glue_test", GLFW.GLFW_KEY_C,
            toggleWithMessage("Grayscale post-effect", client -> TestPostShaderHandler.toggleGrayscale()));

    public static final KeyMapping TOGGLE_SHATTERED = KEYBINDINGS.register(
            "toggle_shattered",
            "key.categories.glue_test", GLFW.GLFW_KEY_V, client -> {
                TestPostShaderHandler.triggerShattered();
                sendMessage(client, "Shattered screen: §aTriggered!");
            });

    public static void registerKeybinds() {
    }

    private static java.util.function.Consumer<Minecraft> toggleWithMessage(String label, Function<Minecraft, Boolean> action) {
        return client -> {
            boolean state = action.apply(client);
            sendMessage(client, label + ": " + (state ? "§aON" : "§cOFF"));
        };
    }

    private static void sendMessage(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("§7[Glue] §f" + message), true);
        }
    }
}
