package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class TestKeybinds {

    public static final KeybindingsRegistry KEYBINDINGS = new KeybindingsRegistry(TestmodClient.MOD_ID);

    public static final KeyMapping TOGGLE_RAYCAST_DEBUG = KEYBINDINGS.register(
            "toggle_raycast_debug",
            "key.categories.glue_test", GLFW.GLFW_KEY_R, client -> TestmodClient.getInstance().toggleRaycastDebug());


    public static void registerKeybinds() {

    }
}
