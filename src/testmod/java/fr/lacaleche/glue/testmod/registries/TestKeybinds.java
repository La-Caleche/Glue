package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.render.PostEffectDebugHud;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry}: registers two keybinds
 * (R = toggle raycast debug, F9 = toggle the post-effect debug HUD) with tick callbacks.
 */
public class TestKeybinds {

    public static final KeybindingsRegistry KEYBINDINGS = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final KeyMapping TOGGLE_RAYCAST_DEBUG = KEYBINDINGS.register(
            "toggle_raycast_debug",
            "key.categories.glue_test", GLFW.GLFW_KEY_R,
            client -> TestmodClient.getInstance().toggleRaycastDebug());

    public static final KeyMapping TOGGLE_POST_EFFECT_HUD = KEYBINDINGS.register(
            "toggle_post_effect_hud",
            "key.categories.glue_test", GLFW.GLFW_KEY_F9,
            client -> PostEffectDebugHud.INSTANCE.toggle());

    public static void registerKeybinds() {
    }
}
