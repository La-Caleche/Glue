package fr.lacaleche.glue.testmod.registries;

import com.mojang.blaze3d.platform.InputConstants;
import fr.lacaleche.glue.registries.KeybindingsRegistry;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.file.FileDialogTestScreen;
import fr.lacaleche.glue.testmod.render.GlueDebugDock;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import fr.lacaleche.glue.testmod.scene.FpsViewportTestScreen;
import fr.lacaleche.glue.testmod.scene.GizmoTestScreen;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Demonstrates Glue's {@link KeybindingsRegistry}: registers every demo keybind with a tick callback.
 *
 * <p>R = raycast debug, F6 = FPS scene, F7 = block scene, F8 = gizmo test, F10 = file dialogs,
 * F11 = demo lights, F12 = the MCSX debug dockspace (Lights + Post FX panes around the embedded
 * game), K = flashlight (eye-attached spot), mouse 5 = spawn a spot.</p>
 */
public class TestKeybinds {

    public static final KeybindingsRegistry KEYBINDINGS = new KeybindingsRegistry(TestmodClient.MOD_ID, TestmodClient::id);

    public static final KeyMapping TOGGLE_RAYCAST_DEBUG = KEYBINDINGS.register(
            "toggle_raycast_debug",
            "key.categories.glue_test", GLFW.GLFW_KEY_R,
            client -> TestmodClient.getInstance().toggleRaycastDebug());

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
            client -> DemoLights.INSTANCE.spawnSpot());

    public static final KeyMapping TOGGLE_DEBUG_DOCK = KEYBINDINGS.register(
            "toggle_debug_dock",
            "key.categories.glue_test", GLFW.GLFW_KEY_F12,
            client -> GlueDebugDock.toggle());

    public static final KeyMapping TOGGLE_STRESS_LIGHTS = KEYBINDINGS.register(
            "toggle_stress_lights",
            "key.categories.glue_test", GLFW.GLFW_KEY_F11,
            client -> DemoLights.INSTANCE.toggleStaticLights());

    public static final KeyMapping TOGGLE_FLASHLIGHT = KEYBINDINGS.register(
            "toggle_flashlight",
            "key.categories.glue_test", GLFW.GLFW_KEY_K,
            client -> DemoLights.INSTANCE.toggleFlashlight());

    public static void registerKeybinds() {
    }
}

