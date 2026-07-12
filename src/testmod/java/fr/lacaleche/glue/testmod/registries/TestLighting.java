package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the deferred colored-light subsystem
 * ({@link fr.lacaleche.glue.client.render.light.LightRenderer}).
 *
 * <p>Toggle with the {@code L} key (see {@link TestKeybinds}). On enable it drops
 * three <b>static</b> colored point lights (red / green / blue) in a ring around
 * you (point lights use screen-space contact shadows), plus one bright white
 * <b>spot</b> mounted overhead and aimed at the ground &mdash; that spot renders a
 * real <b>shadow map</b>, so blocks under it cast clean, view-independent
 * shadows. Walk around to compare the two shadow techniques.</p>
 */
public final class TestLighting {

    private static boolean enabled = false;
    private static final List<Light> staticLights = new ArrayList<>();
    private static Light flashlight;

    private TestLighting() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!enabled) return;
            LocalPlayer player = client.player;
            if (player == null) return;
            updateFlashlight(player);
        });
    }

    public static void toggle() {
        enabled = !enabled;
        if (enabled) {
            LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) placeStaticLights(player);
        } else {
            removeAll();
        }
    }

    private static void placeStaticLights(LocalPlayer player) {
        LightManager manager = LightManager.getInstance();
        Vec3 p = player.position();
        double y = p.y + 0.9;
        float range = 9.0f;
        float intensity = 2.5f;

//        staticLights.add(manager.add(Light.point(p.x + 3.0, y, p.z, 1.0f, 0.15f, 0.1f, intensity, range))); // red
//        staticLights.add(manager.add(Light.point(p.x - 3.0, y, p.z, 0.15f, 1.0f, 0.2f, intensity, range))); // green
//        staticLights.add(manager.add(Light.point(p.x, y, p.z + 3.0, 0.15f, 0.3f, 1.0f, intensity, range))); // blue

        // Shadow-casting spot: mounted ~7 blocks up and offset, aimed at the
        // ground near the player so nearby blocks throw real shadow-mapped shadows.
        // 1. Get player eye position (where the camera actually is)
        Vec3 eyePos = player.getEyePosition();

        // 2. Get the camera's look direction vector (normalized by default)
        Vec3 dir = player.getViewVector(1.0f);

        // 3. Define the light's source position.
        // If you want it exactly AT the camera eye level:
        double sx = eyePos.x;
        double sy = eyePos.y;
        double sz = eyePos.z;

        // Optional: If you want the light offset slightly behind or above the player
        // so you can see the beam/shadows better, uncomment the lines below:
        // double offsetDist = 1.5;
        // sx -= dir.x * offsetDist;
        // sy -= dir.y * offsetDist;
        // sz -= dir.z * offsetDist;

        // 4. Add the spot light facing the player's look direction
        staticLights.add(manager.add(Light.spot(
                sx, sy, sz,
                (float) dir.x, (float) dir.y, (float) dir.z,
//                1.0f, 0.95f, 0.85f,  // warm white
                0.95f, 0.921f, 0.77f,
                3.0f,                // intensity
                22.0f,               // range
                20.0f,               // inner half-angle (deg)
                32.0f)));            // outer half-angle (deg)
    }

    private static void updateFlashlight(LocalPlayer player) {
        LightManager manager = LightManager.getInstance();
        if (flashlight != null) manager.remove(flashlight);
        if (true) return ;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        flashlight = manager.add(Light.spot(
                eye.x, eye.y, eye.z,
                (float) look.x, (float) look.y, (float) look.z,
                1.0f, 0.85f, 0.6f,   // warm white
                3.0f,                // intensity
                26.0f,               // range
                12.0f,               // inner half-angle (deg)
                22.0f));             // outer half-angle (deg)
    }

    /**
     * Track a light created elsewhere (the light debug HUD's "add" actions) so the
     * toggle-off sweep removes it with the rest.
     */
    public static void track(Light light) {
        if (light != null) staticLights.add(light);
    }

    /**
     * The light debug HUD edits by swap: {@code Light} is immutable, so every edit
     * removes the old instance from the {@link LightManager} and adds a rebuilt one.
     * Mirror the swap here, or {@link #removeAll()} would try to remove instances
     * that no longer exist and leak the replacements.
     */
    public static void onLightReplaced(Light oldLight, Light newLight) {
        int i = staticLights.indexOf(oldLight);  // Light has no equals() -> identity
        if (i >= 0) {
            staticLights.set(i, newLight);
        } else {
            staticLights.add(newLight);
        }
    }

    /** The light debug HUD deleted a light; forget it here too. */
    public static void onLightRemoved(Light light) {
        staticLights.remove(light);
    }

    private static void removeAll() {
        LightManager manager = LightManager.getInstance();
        for (Light light : staticLights) {
            manager.remove(light);
        }
        staticLights.clear();
        if (flashlight != null) {
            manager.remove(flashlight);
            flashlight = null;
        }
    }
}
