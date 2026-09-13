package fr.lacaleche.glue.client.registries;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Supplier;

/**
 * Two-layer registry: permanent Java entries + hot-reloadable JSON entries.
 * JSON overrides Java for the same ID. {@link #reload(Map)} atomically
 * replaces the JSON layer — deleted files simply disappear.
 *
 * @param <T> the type of entries stored in this registry
 */
public class ReloadableRegistry<T> implements Iterable<Map.Entry<ResourceLocation, T>> {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/registry");

    private final String name;
    private final Map<ResourceLocation, T> javaEntries = new LinkedHashMap<>();
    private volatile Map<ResourceLocation, T> jsonEntries = Collections.emptyMap();

    /** Bumped on every {@link #reload(Map)} so consumers can invalidate caches built from JSON entries. */
    private volatile int version = 0;

    public ReloadableRegistry(String name) {
        this.name = name;
    }

    /** Registers a permanent (Java) entry. Survives resource reloads. */
    public T register(ResourceLocation id, T value) {
        if (javaEntries.containsKey(id)) {
            LOGGER.warn("[Glue] Duplicate Java registration in '{}': {}", name, id);
        }
        javaEntries.put(id, value);
        return value;
    }

    /** Atomically replaces all JSON-loaded entries and bumps {@link #version()}. */
    public void reload(Map<ResourceLocation, T> entries) {
        this.jsonEntries = Map.copyOf(entries);
        this.version++;
    }

    /**
     * Monotonically increasing counter, incremented on every {@link #reload(Map)}.
     * Consumers that cache instances derived from JSON entries can compare this
     * against the version they last built at to detect a reload and rebuild.
     */
    public int version() {
        return version;
    }

    /** Looks up an entry by ID. JSON layer takes priority. */
    @Nullable
    public T get(ResourceLocation id) {
        T json = jsonEntries.get(id);
        if (json != null) return json;
        return javaEntries.get(id);
    }

    /**
     * Looks up an entry, registering the supplied fallback as a permanent Java entry when neither
     * layer contains the id. Never returns null: the id keeps resolving on every later lookup,
     * while a JSON entry with the same id still overrides the registered fallback (and its removal
     * on reload falls back to it cleanly). The fallback supplier is only invoked on a miss.
     *
     * @throws IllegalArgumentException if the supplier is null
     * @throws IllegalStateException    if the supplier returns null, which would leave the id
     *                                  present but unresolvable
     */
    public T getOrRegister(ResourceLocation id, Supplier<? extends T> fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("Fallback supplier for '" + id + "' in registry '" + name + "' must not be null");
        }

        T existing = get(id);
        if (existing != null) return existing;

        T created = fallback.get();
        if (created == null) {
            throw new IllegalStateException("Fallback supplier for '" + id + "' in registry '" + name + "' returned null");
        }

        return register(id, created);
    }

    public boolean containsKey(ResourceLocation id) {
        return jsonEntries.containsKey(id) || javaEntries.containsKey(id);
    }

    /** Returns a merged, unmodifiable view. JSON overrides Java for same ID. */
    public Map<ResourceLocation, T> getAll() {
        Map<ResourceLocation, T> merged = new LinkedHashMap<>(javaEntries);
        merged.putAll(jsonEntries);
        return Collections.unmodifiableMap(merged);
    }

    /** Number of distinct ids across both layers, without allocating a merged map. */
    public int size() {
        Map<ResourceLocation, T> json = jsonEntries;
        int count = json.size();
        for (ResourceLocation id : javaEntries.keySet()) {
            if (!json.containsKey(id)) count++;
        }
        return count;
    }

    @Override
    public Iterator<Map.Entry<ResourceLocation, T>> iterator() {
        return getAll().entrySet().iterator();
    }

    public String getName() {
        return name;
    }
}
