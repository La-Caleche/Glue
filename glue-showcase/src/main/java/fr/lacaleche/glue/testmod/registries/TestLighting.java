package fr.lacaleche.glue.testmod.registries;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightAttachments;
import fr.lacaleche.glue.client.render.light.LightHandle;
import fr.lacaleche.glue.client.render.light.LightManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Demonstrates the deferred colored-light subsystem
 * ({@link fr.lacaleche.glue.client.render.light.LightRenderer}).
 *
 * <p>Toggle with the {@code L} key (see {@link TestKeybinds}). On enable it drops
 * three <b>static</b> shadowed point lights, a 24-light unshadowed stress ring,
 * one block attachment, and one entity-eye <b>spot</b>. The mix demonstrates
 * independent resident/update budgets and frame-sampled movement.</p>
 */
public final class TestLighting {

    private static boolean enabled = false;
    private static final List<Light> STATIC_LIGHTS = new ArrayList<>();
    private static LightHandle flashlight;
    private static LightHandle blockLight;

    private TestLighting() {
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

        STATIC_LIGHTS.add(manager.add(Light.point(p.x + 3.0, y, p.z,
                1.0f, 0.15f, 0.1f, intensity, range)));
        STATIC_LIGHTS.add(manager.add(Light.point(p.x - 3.0, y, p.z,
                0.15f, 1.0f, 0.2f, intensity, range)));
        STATIC_LIGHTS.add(manager.add(Light.point(p.x, y, p.z + 3.0,
                0.15f, 0.3f, 1.0f, intensity, range)));

        // A 24-light no-shadow ring exercises the intended 10-50 visible-light range
        // without consuming shadow slots or forcing shadow-map updates.
        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0;
            float r = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle)));
            float g = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle - Math.PI * 2.0 / 3.0)));
            float b = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle + Math.PI * 2.0 / 3.0)));
            STATIC_LIGHTS.add(manager.add(Light.point(
                    p.x + Math.cos(angle) * 10.0, y, p.z + Math.sin(angle) * 10.0,
                    r, g, b, 1.25f, 5.0f).withShadow(false)));
        }

        Light blockDefinition = Light.point(0, 0, 0,
                1.0f, 0.45f, 0.12f, 2.0f, 7.0f).withShadow(false);
        blockLight = manager.attach(blockDefinition,
                LightAttachments.block(BlockPos.containing(p).above(2)));

        // Shadow-casting flashlight sampled from the player's interpolated eye
        // position and view direction every rendered frame.
        Light definition = Light.spot(
                0, 0, 0,
                0, -1, 0,
                0.95f, 0.921f, 0.77f,
                3.0f, 22.0f, 20.0f, 32.0f);
        flashlight = manager.attach(definition, LightAttachments.entityEyes(player));
    }

    /**
     * Track a light created elsewhere (the light debug HUD's "add" actions) so the
     * toggle-off sweep removes it with the rest.
     */
    public static void track(Light light) {
        if (light != null) STATIC_LIGHTS.add(light);
    }

    /**
     * The light debug HUD edits by swap: {@code Light} is immutable, so every edit
     * removes the old instance from the {@link LightManager} and adds a rebuilt one.
     * Mirror the swap here, or {@link #removeAll()} would try to remove instances
     * that no longer exist and leak the replacements.
     */
    public static void onLightReplaced(Light oldLight, Light newLight) {
        int i = STATIC_LIGHTS.indexOf(oldLight);  // Light has no equals() -> identity
        if (i >= 0) {
            STATIC_LIGHTS.set(i, newLight);
        } else {
            STATIC_LIGHTS.add(newLight);
        }
    }

    /** The light debug HUD deleted a light; forget it here too. */
    public static void onLightRemoved(Light light) {
        STATIC_LIGHTS.remove(light);
    }

    private static void removeAll() {
        LightManager manager = LightManager.getInstance();
        for (Light light : STATIC_LIGHTS) {
            manager.remove(light);
        }
        STATIC_LIGHTS.clear();
        if (flashlight != null) {
            flashlight.remove();
            flashlight = null;
        }
        if (blockLight != null) {
            blockLight.remove();
            blockLight = null;
        }
    }
}
