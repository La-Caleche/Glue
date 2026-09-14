package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.jcef.JcefDemo;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry} without reserving a key for every demo.
 *
 * <p>R toggles raycast debugging and F6 opens the JCEF experiment.</p>
 */
public final class TestKeybinds {

    private static KeybindingsRegistry keybindings;

    private TestKeybinds() {
    }

    public static void register() {
        if (keybindings != null) throw new IllegalStateException("Showcase keybindings are already registered");

        keybindings = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);
        keybindings.register(
                "toggle_raycast_debug",
                "key.categories.glue_test",
                GLFW.GLFW_KEY_R,
                client -> TestmodClient.getInstance().toggleRaycastDebug()
        );
        keybindings.register(
                "open_showcase",
                "key.categories.glue_test",
                GLFW.GLFW_KEY_F6,
                client -> JcefDemo.open(client, false)
        );
    }
}
