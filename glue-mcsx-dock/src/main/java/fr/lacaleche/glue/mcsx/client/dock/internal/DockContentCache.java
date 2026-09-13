package fr.lacaleche.glue.mcsx.client.dock.internal;

import fr.lacaleche.glue.mcsx.client.dock.DockContent;
import fr.lacaleche.glue.mcsx.client.dock.DockPane;
import fr.lacaleche.glue.mcsx.dock.McsxDock;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owns all lazily created pane views and their exactly-once disposal. */
@Environment(EnvType.CLIENT)
public final class DockContentCache implements AutoCloseable {

    private final Context context;
    private final Map<String, DockPane> panes;
    private final Map<String, Entry> contentEntries = new LinkedHashMap<>();
    private final Map<String, Entry> trailingHeaderEntries = new LinkedHashMap<>();
    private boolean closed;

    public DockContentCache(Context context, Map<String, DockPane> panes) {
        this.context = Objects.requireNonNull(context, "context");
        this.panes = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(panes, "panes")));
    }

    public View getContent(String paneId) {
        DockPane pane = this.requirePane(paneId);
        return this.getOrCreate(pane, pane.content(), this.contentEntries, "content");
    }

    public View getTrailingHeader(String paneId) {
        DockPane pane = this.requirePane(paneId);
        DockContent trailingHeader = pane.trailingHeader();
        if (trailingHeader == null) return null;

        return this.getOrCreate(pane, trailingHeader, this.trailingHeaderEntries, "trailing header");
    }

    @Override
    public void close() {
        if (this.closed) return;

        this.closed = true;
        RuntimeException failure = null;
        failure = dispose(this.contentEntries, failure);
        failure = dispose(this.trailingHeaderEntries, failure);
        this.contentEntries.clear();
        this.trailingHeaderEntries.clear();
        if (failure != null) throw failure;
    }

    private DockPane requirePane(String paneId) {
        if (this.closed) throw new IllegalStateException("Dock content cache is closed");

        DockPane pane = this.panes.get(Objects.requireNonNull(paneId, "paneId"));
        if (pane == null) throw new IllegalArgumentException("Unknown dock pane: " + paneId);
        return pane;
    }

    private View getOrCreate(DockPane pane, DockContent content, Map<String, Entry> entries, String role) {
        Entry existing = entries.get(pane.id());
        if (existing != null) return existing.view;

        View view = content.create(this.context);
        if (view == null) throw new IllegalStateException("Dock pane " + role + " returned null: " + pane.id());

        entries.put(pane.id(), new Entry(pane.id(), role, content, view));
        return view;
    }

    private static RuntimeException dispose(Map<String, Entry> entries, RuntimeException failure) {
        RuntimeException result = failure;
        for (Entry entry : entries.values()) {
            try {
                entry.content.dispose(entry.view);
            } catch (RuntimeException exception) {
                McsxDock.LOGGER.warn("Failed to dispose dock pane '{}' {}", entry.paneId, entry.role, exception);
                if (result == null) result = exception;
                else if (result != exception) result.addSuppressed(exception);
            }
        }
        return result;
    }

    private record Entry(String paneId, String role, DockContent content, View view) {
    }
}
