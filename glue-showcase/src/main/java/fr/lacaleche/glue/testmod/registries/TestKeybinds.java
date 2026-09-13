package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.controls.ShowcaseControlScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry} without reserving a key for every demo.
 *
 * <p>R toggles raycast debugging and F6 opens the showcase control center.</p>
 */
public final class TestKeybinds {

    private static KeybindingsRegistry keybindings;
    private static KeyMapping toggleRaycastDebug;
    private static KeyMapping openShowcase;

    private TestKeybinds() {
    }

    public static void register() {
        if (keybindings != null) throw new IllegalStateException("Showcase keybindings are already registered");

        keybindings = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);
        toggleRaycastDebug = keybindings.register(
                "toggle_raycast_debug",
                "key.categories.glue_test",
                GLFW.GLFW_KEY_R,
                client -> TestmodClient.getInstance().toggleRaycastDebug()
        );
        openShowcase = keybindings.register(
                "open_showcase",
                "key.categories.glue_test",
                GLFW.GLFW_KEY_F6,
                ShowcaseControlScreen::open
        );
    }

    public static Component toggleRaycastDebugKey() {
        return requireRegistered(toggleRaycastDebug).getTranslatedKeyMessage();
    }

    public static Component openShowcaseKey() {
        return requireRegistered(openShowcase).getTranslatedKeyMessage();
    }

    private static KeyMapping requireRegistered(KeyMapping keyMapping) {
        if (keyMapping == null) throw new IllegalStateException("Showcase keybindings are not registered");
        return keyMapping;
    }
}
