package fr.lacaleche.glue.client.render.light;

import java.util.ArrayList;
import java.util.List;

/**
 * The mutable set of active {@link Light}s. Mods add and remove lights here; the
 * {@link LightRenderer} reads a {@link #snapshot()} once per frame so mutation
 * during rendering is safe.
 *
 * <p>This is the path-independent "light list" stage of the deferred lighting
 * pipeline &mdash; it is unaware of vanilla vs Iris rendering.</p>
 */
public final class LightManager {

    private static final LightManager INSTANCE = new LightManager();

    private final List<Light> lights = new ArrayList<>();

    private LightManager() {
    }

    public static LightManager getInstance() {
        return INSTANCE;
    }

    /** Registers a light. Returns the same instance for convenient field storage. */
    public synchronized Light add(Light light) {
        if (light != null) {
            lights.add(light);
        }
        return light;
    }

    /** Removes a previously added light. No-op if absent or null. */
    public synchronized void remove(Light light) {
        if (light != null) {
            lights.remove(light);
        }
    }

    /** Removes every light. */
    public synchronized void clear() {
        lights.clear();
    }

    public synchronized boolean isEmpty() {
        return lights.isEmpty();
    }

    /**
     * A stable copy of the current light list for one frame's rendering pass.
     */
    public synchronized List<Light> snapshot() {
        return new ArrayList<>(lights);
    }
}
