package fr.lacaleche.glue.testmod.registries;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.file.FileDialogTestScreen;
import fr.lacaleche.glue.testmod.render.LightDebugHud;
import fr.lacaleche.glue.testmod.render.PostEffectDebugHud;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry}: registers keybinds
 * (R = toggle raycast debug, F9 = toggle the post-effect debug HUD,
 * F6 = FPS scene, F7 = block scene test, F8 = gizmo test,
 * F10 = file dialog test) with tick callbacks.
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

    public static final KeyMapping OPEN_FPS_SCENE = KEYBINDINGS.register(
            "open_fps_scene",
            "key.categories.glue_test", GLFW.GLFW_KEY_F6,
            client -> client.setScreen(new FpsViewportTestScreen()));

    public static final KeyMapping OPEN_BLOCK_SCENE = KEYBINDINGS.register(
            "open_block_scene",
            "key.categories.glue_test", GLFW.GLFW_KEY_F7,
            client -> client.setScreen(new BlockSceneTestScreen()));

    public static final KeyMapping OPEN_GIZMO_TEST = KEYBINDINGS.register(
            "open_gizmo_test",
            "key.categories.glue_test", GLFW.GLFW_KEY_F8,
            client -> client.setScreen(new GizmoTestScreen()));

    public static final KeyMapping OPEN_FILE_DIALOG_TEST = KEYBINDINGS.register(
            "open_file_dialog_test",
            "key.categories.glue_test", GLFW.GLFW_KEY_F10,
            client -> client.setScreen(new FileDialogTestScreen()));

    public static final KeyMapping ADD_SPOT = KEYBINDINGS.register(
            "add_spot",
            "key.categories.glue_test", GLFW.GLFW_MOUSE_BUTTON_5,
            InputConstants.Type.MOUSE,
            client -> TestLighting.addStaticSpot());

    public static final KeyMapping TOGGLE_LIGHT_HUD = KEYBINDINGS.register(
            "toggle_light_hud",
            "key.categories.glue_test", GLFW.GLFW_KEY_F12,
            client -> LightDebugHud.INSTANCE.toggle());

    public static final KeyMapping TOGGLE_STRESS_LIGHTS = KEYBINDINGS.register(
            "toggle_stress_lights",
            "key.categories.glue_test", GLFW.GLFW_KEY_F11,
            client -> TestLighting.toggle());

    public static final KeyMapping ADD_PERSISTENT_LIGHT = KEYBINDINGS.register(
            "add_persistent_light",
            "key.categories.glue_test", GLFW.GLFW_KEY_P,
            client -> TestLighting.addPersistentPoint());

    public static void registerKeybinds() {
    }
}

