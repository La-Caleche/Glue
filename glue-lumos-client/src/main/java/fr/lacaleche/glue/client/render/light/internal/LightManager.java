package fr.lacaleche.glue.client.render.light.internal;

import fr.lacaleche.glue.lumos.LightAttachment;
import fr.lacaleche.glue.lumos.LightHandle;
import fr.lacaleche.glue.client.render.light.internal.context.WorldLightContext;
import fr.lacaleche.glue.lumos.Light;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

/**
 * The mutable set of active {@link Light}s. Mods do not touch this directly &mdash; they go through
 * {@link fr.lacaleche.glue.lumos.Lumos}. The
 * {@link fr.lacaleche.glue.client.render.light.LightRenderer} reads a {@link #snapshot()} once per
 * frame, so mutation during rendering is safe.
 *
 * <p>This is the path-independent "light list" stage of the deferred lighting
 * pipeline &mdash; it is unaware of vanilla vs Iris rendering.</p>
 */
public final class LightManager {

    private static final LightManager INSTANCE = new LightManager();

    @Nullable
    private WorldLightContext context;

    private LightManager() {
    }

    public static LightManager getInstance() {
        return INSTANCE;
    }

    /** The active world's lighting context, or {@code null} outside a world. */
    @Nullable
    public synchronized WorldLightContext currentWorld() {
        return context;
    }

    @Nullable
    public synchronized WorldLightContext switchWorld(@Nullable ClientLevel level) {
        if (context != null && context.level() == level) return null;
        WorldLightContext previous = context;
        context = level == null ? null : new WorldLightContext(level);
        return previous;
    }

    /** Registers a light. Returns the same instance for convenient field storage. */
    public synchronized Light add(Light light) {
        return requireWorld().add(light);
    }

    /** Registers a light whose transform is sampled once per rendered frame. */
    public synchronized LightHandle attach(Light light, LightAttachment attachment) {
        return requireWorld().attach(light, attachment);
    }

    /** Removes a previously added light. No-op if absent or null. */
    public synchronized void remove(Light light) {
        if (context != null) context.remove(light);
    }

    /** Removes every light. */
    public synchronized void clear() {
        if (context != null) context.clear();
    }

    public synchronized boolean isEmpty() {
        return context == null || context.isEmpty();
    }

    /**
     * A stable copy of the current light list for one frame's rendering pass.
     */
    public synchronized List<Light> snapshot() {
        return snapshot(1f);
    }

    public synchronized List<Light> snapshot(float partialTick) {
        return context == null ? List.of() : context.snapshot(partialTick);
    }

    private WorldLightContext requireWorld() {
        if (context == null) {
            throw new IllegalStateException("Cannot register a Lumos light outside a client world");
        }
        return context;
    }
}
