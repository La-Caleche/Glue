package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.TestGuiOverlayRenderer;
import fr.lacaleche.glue.testmod.render.TestPostShaderHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class TestKeybinds {

    public static final KeybindingsRegistry KEYBINDINGS = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final KeyMapping TOGGLE_RAYCAST_DEBUG = KEYBINDINGS.register(
            "toggle_raycast_debug",
            "key.categories.glue_test", GLFW.GLFW_KEY_R, client -> TestmodClient.getInstance().toggleRaycastDebug());

    public static final KeyMapping TOGGLE_BLUR = KEYBINDINGS.register(
            "toggle_blur",
            "key.categories.glue_test", GLFW.GLFW_KEY_B, client -> {
                boolean state = TestPostShaderHandler.toggleBlur();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§7[Glue] §fBlur post-effect: " + (state ? "§aON" : "§cOFF")),
                            true);
                }
            });

    public static final KeyMapping TOGGLE_GUI_OVERLAY = KEYBINDINGS.register(
            "toggle_gui_overlay",
            "key.categories.glue_test", GLFW.GLFW_KEY_G, client -> {
                boolean state = TestGuiOverlayRenderer.toggle();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§7[Glue] §fGUI overlay: " + (state ? "§aON" : "§cOFF")),
                            true);
                }
            });

    public static final KeyMapping TOGGLE_SHATTERED = KEYBINDINGS.register(
            "toggle_shattered",
            "key.categories.glue_test", GLFW.GLFW_KEY_V, client -> {
                TestPostShaderHandler.triggerShattered();
                if (client.player != null) {
                    client.player.displayClientMessage(
                            Component.literal("§7[Glue] §fShattered screen: §aTriggered!"),
                            true);
                }
            });

    public static void registerKeybinds() {
    }
}
