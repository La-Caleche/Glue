package fr.lacaleche.glue.testmod.lumos;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns the showcase's <b>visual</b> demo lights: {@link Lumos#spawn}ed, seen by this client alone,
 * gone with the session. World lights &mdash; {@link Lumos#place}d, saved and synced &mdash; are
 * demonstrated by the light debug HUD, which places and edits them through the client request channel
 * {@code Testmod} opens.
 *
 * <p>It keeps its own list because {@link Lumos#active} reports every light in the world, including
 * the server's, and a demo may only clean up what it spawned. Anything that mutates a demo light goes
 * through {@link #replace} or {@link #remove} so the list cannot drift &mdash; the debug HUD included.</p>
 */
@Environment(EnvType.CLIENT)
public final class DemoLights {

    public static final DemoLights INSTANCE = new DemoLights();

    private final List<Light> spawned = new ArrayList<>();
    private boolean enabled;

    private DemoLights() {
    }

    /** Spawns a visual light and takes ownership of it. Returns it, for {@link #replace}/{@link #remove}. */
    public Light spawn(Level level, Light light) {
        spawned.add(Lumos.spawn(level, light));
        return light;
    }

    /**
     * Swaps a demo light for a rebuilt one. {@code Light} is immutable and the shadow and glass caches
     * key on identity, so despawn-then-spawn is what invalidates them and triggers the re-bake.
     */
    public void replace(Level level, Light oldLight, Light newLight) {
        Lumos.despawn(level, oldLight);
        Lumos.spawn(level, newLight);
        int i = spawned.indexOf(oldLight);  // Light has no equals() -> identity
        if (i >= 0) spawned.set(i, newLight);
    }

    public void remove(Level level, Light light) {
        Lumos.despawn(level, light);
        spawned.remove(light);
    }

    /** F11: three static shadowed point lights plus a 24-light unshadowed ring, or clear them all. */
    public void toggleStaticLights() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        enabled = !enabled;
        if (enabled) {
            spawnStaticLights(player);
        } else {
            despawnAll(player.level());
        }
    }

    public void spawnSpot() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;
        Vec3 position = player.getEyePosition();
        Vec3 direction = player.getViewVector(0f);

        spawn(player.level(), Light.spot(
                position.x, position.y, position.z,
                (float) direction.x, (float) direction.y, (float) direction.z,
                0.95f, 0.921f, 0.77f,
                3.0f, 22.0f, 20.0f, 32.0f));
    }

    private void spawnStaticLights(LocalPlayer player) {
        Level level = player.level();
        Vec3 p = player.position();
        double y = p.y + 0.9;
        float range = 9.0f;
        float intensity = 2.5f;

        spawn(level, Light.point(p.x + 3.0, y, p.z, 1.0f, 0.15f, 0.1f, intensity, range));
        spawn(level, Light.point(p.x - 3.0, y, p.z, 0.15f, 1.0f, 0.2f, intensity, range));
        spawn(level, Light.point(p.x, y, p.z + 3.0, 0.15f, 0.3f, 1.0f, intensity, range));

        for (int i = 0; i < 24; i++) {
            double angle = Math.PI * 2.0 * i / 24.0;
            float r = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle)));
            float g = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle - Math.PI * 2.0 / 3.0)));
            float b = (float) (0.25 + 0.75 * Math.max(0.0, Math.cos(angle + Math.PI * 2.0 / 3.0)));
            spawn(level, Light.point(
                    p.x + Math.cos(angle) * 10.0, y, p.z + Math.sin(angle) * 10.0,
                    r, g, b, 1.25f, 5.0f).withShadow(false));
        }
    }

    private void despawnAll(Level level) {
        for (Light light : spawned) {
            Lumos.despawn(level, light);
        }
        spawned.clear();
    }
}
