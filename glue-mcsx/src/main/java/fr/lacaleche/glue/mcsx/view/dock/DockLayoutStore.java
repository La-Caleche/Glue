package fr.lacaleche.glue.mcsx.view.dock;

import fr.lacaleche.glue.mcsx.core.dock.DockIds;
import fr.lacaleche.glue.mcsx.core.dock.DockLayout;
import fr.lacaleche.glue.mcsx.core.dock.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.core.dock.DockLayoutException;
import fr.lacaleche.glue.mcsx.core.dock.DockLeaf;
import fr.lacaleche.glue.mcsx.core.dock.DockOps;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads and persists a dockspace's layout. Resolution order: the user's mutated layout
 * ({@code config/mcsx/dock/<id>.json}), then the configured default (a Java-built
 * {@link DockLayout} or a {@code "ns:name"} classpath asset at {@code assets/ns/dock/name.json}),
 * then one leaf holding every pane. Whatever loads is sanitized against the registered pane set —
 * a stale file never puts a ghost pane on screen — and anything unreadable falls back a level
 * with a warning instead of failing the open.
 */
final class DockLayoutStore {

    private static final Logger LOGGER = LoggerFactory.getLogger("mcsx-dock");

    private DockLayoutStore() {
    }

    static Path userFile(String workspaceId) {
        Path directory = FabricLoader.getInstance().getConfigDir()
                .resolve("mcsx").resolve("dock").normalize();
        Path file = directory.resolve(workspaceId + ".json").normalize();
        if (!file.startsWith(directory)) {
            throw new IllegalArgumentException("workspace id escapes the dock config directory");
        }
        return file;
    }

    static DockLayout load(DockConfig config, DockIds ids, List<DockPane> panes) {
        Set<String> knownPanes = knownPanes(panes);
        Path file = userFile(config.id());
        if (config.persist() && Files.isRegularFile(file)) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                return DockOps.sanitize(DockLayoutCodec.read(json, ids), knownPanes);
            } catch (IOException | DockLayoutException e) {
                LOGGER.warn("Ignoring unreadable dock layout {} ({})", file, e.getMessage());
            }
        }
        return DockOps.sanitize(defaultLayout(config, ids, panes), knownPanes);
    }

    static void save(DockConfig config, DockLayout layout) {
        if (!config.persist()) {
            return;
        }
        Path file = userFile(config.id());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, DockLayoutCodec.write(layout), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.warn("Failed to save dock layout {} ({})", file, e.getMessage());
        }
    }

    static void reset(DockConfig config) {
        try {
            Files.deleteIfExists(userFile(config.id()));
        } catch (IOException e) {
            LOGGER.warn("Failed to delete dock layout for {} ({})", config.id(), e.getMessage());
        }
    }

    static DockLayout defaultLayout(DockConfig config, DockIds ids, List<DockPane> panes) {
        if (config.defaultLayout() != null) {
            // round-trip through the codec: same structure, ids re-minted by this workspace
            return DockLayoutCodec.read(DockLayoutCodec.write(config.defaultLayout()), ids);
        }
        if (config.defaultLayoutAsset() != null) {
            try {
                return DockLayoutCodec.read(readAsset(config.defaultLayoutAsset()), ids);
            } catch (UncheckedIOException | IllegalArgumentException | DockLayoutException e) {
                LOGGER.warn("Ignoring unreadable default dock layout '{}' ({})",
                        config.defaultLayoutAsset(), e.getMessage());
            }
        }
        List<String> paneIds = panes.stream().map(DockPane::id).toList();
        if (paneIds.isEmpty()) {
            return DockLayout.empty();
        }
        return new DockLayout(DockLeaf.of(ids, paneIds), List.of());
    }

    static Set<String> knownPanes(List<DockPane> panes) {
        Set<String> known = new HashSet<>();
        for (DockPane pane : panes) {
            known.add(pane.id());
        }
        return known;
    }

    /** {@code "ns:name"} → classpath resource {@code assets/ns/dock/name.json}. */
    private static String readAsset(String asset) {
        int colon = asset.indexOf(':');
        if (colon <= 0 || colon == asset.length() - 1) {
            throw new IllegalArgumentException("layout asset must be 'namespace:name', got '" + asset + "'");
        }
        String path = "assets/" + asset.substring(0, colon) + "/dock/" + asset.substring(colon + 1) + ".json";
        try (InputStream in = DockLayoutStore.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("no dock layout asset at " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read dock layout asset " + path, e);
        }
    }
}
