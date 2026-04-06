package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
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
            "key.categories.glue_test", GLFW.GLFW_KEY_V,
            triggerWithMessage("Shattered screen", TestPostShaderHandler.SHATTERED));

    public static final KeyMapping TRIGGER_DEPARTURE = KEYBINDINGS.register(
            "trigger_departure",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_7,
            triggerWithMessage("§dDeparture Vortex", TestPostShaderHandler.DEPARTURE));

    public static final KeyMapping TRIGGER_ARRIVAL = KEYBINDINGS.register(
            "trigger_arrival",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_8,
            triggerWithMessage("§eArrival Shockwave", TestPostShaderHandler.ARRIVAL));

    public static final KeyMapping TRIGGER_DENIAL = KEYBINDINGS.register(
            "trigger_denial",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_9,
            triggerWithMessage("§cEnd-Locked Denial", TestPostShaderHandler.DENIAL));

    public static final KeyMapping TRIGGER_SUN = KEYBINDINGS.register(
            "trigger_sun",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_0,
            triggerWithMessage("§6Sun Surface", TestPostShaderHandler.SUN));

    public static final KeyMapping TRIGGER_CHROMATIC = KEYBINDINGS.register(
            "trigger_chromatic",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_4,
            triggerWithMessage("§bChromatic Aberration", TestPostShaderHandler.CHROMATIC));

    public static final KeyMapping TRIGGER_IMPACT = KEYBINDINGS.register(
            "trigger_impact",
            "key.categories.glue_test", GLFW.GLFW_KEY_KP_5,
            triggerWithMessage("§fImpact Frame", TestPostShaderHandler.IMPACT));

    public static void registerKeybinds() {
    }

    private static Consumer<Minecraft> toggleWithMessage(String label, Function<Minecraft, Boolean> action) {
        return client -> {
            boolean state = action.apply(client);
            sendMessage(client, label + ": " + (state ? "§aON" : "§cOFF"));
        };
    }

    private static Consumer<Minecraft> triggerWithMessage(String label, fr.lacaleche.glue.testmod.render.TimedEffect effect) {
        return client -> {
            effect.trigger();
            sendMessage(client, label + ": §aTriggered!");
        };
    }

    private static void sendMessage(Minecraft client, String message) {
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal("§7[Glue] §f" + message), true);
        }
    }
}
