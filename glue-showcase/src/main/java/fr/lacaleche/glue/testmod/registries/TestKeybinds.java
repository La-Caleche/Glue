package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.ui.ShowcaseMenu;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry} without reserving a key for every demo.
 *
 * <p>R opens the showcase menu, as {@code /showcase} does, where the other demos are reached.</p>
 */
public final class TestKeybinds {

    private static KeybindingsRegistry keybindings;

    private TestKeybinds() {
    }

    public static void register() {
        if (keybindings != null) throw new IllegalStateException("Showcase keybindings are already registered");

        keybindings = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);
        keybindings.register(
                "open_showcase_menu",
                "key.categories.glue_test",
                GLFW.GLFW_KEY_R,
                client -> ShowcaseMenu.open()
        );
    }
}
