package fr.lacaleche.glue.client.debug.internal;

import fr.lacaleche.glue.client.ui.UiPage;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The pages of the developer menu, grouped by their id's namespace in the order the namespaces first
 * registered, each group in registration order. Registration closes once the client has started.
 */
public final class DeveloperPages {

    private final Map<String, List<Entry>> groups = new LinkedHashMap<>();
    private final Set<ResourceLocation> ids = new HashSet<>();
    private boolean closed;

    public void register(ResourceLocation id, boolean needsWorld, Supplier<UiPage> page) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(page, "page");
        if (this.closed) throw new IllegalStateException("Developer menu pages register before the client starts: " + id);
        if (!this.ids.add(id)) throw new IllegalArgumentException("Duplicate developer menu page: " + id);

        this.groups.computeIfAbsent(id.getNamespace(), namespace -> new ArrayList<>())
                .add(new Entry(id, needsWorld, page));
    }

    public void close() {
        this.closed = true;
    }

    /** The pages by namespace, each list in registration order. */
    public Map<String, List<Entry>> groups() {
        Map<String, List<Entry>> copy = new LinkedHashMap<>();
        this.groups.forEach((namespace, entries) -> copy.put(namespace, List.copyOf(entries)));
        return copy;
    }

    /**
     * A registered page.
     *
     * @param needsWorld whether the page is unavailable while no world is loaded
     * @param page creates the page each time the menu opens
     */
    public record Entry(ResourceLocation id, boolean needsWorld, Supplier<UiPage> page) {
    }
}
