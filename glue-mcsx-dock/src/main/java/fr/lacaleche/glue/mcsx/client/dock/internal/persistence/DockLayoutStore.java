package fr.lacaleche.glue.mcsx.client.dock.internal.persistence;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockOperations;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.dock.McsxDock;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.FileUtil;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/** Resolves and safely persists sanitized workspace layouts. */
@Environment(EnvType.CLIENT)
public final class DockLayoutStore {

    private static final FileToIdConverter RESOURCES = FileToIdConverter.json("mcsx/dock");
    private static final String EXTENSION = ".json";
    private static final String BACKUP_SUFFIX = "_old";

    private final Path root;
    private final ResourceProvider resources;
    private final Executor writer;
    private final Map<Path, CompletableFuture<Void>> writes = new ConcurrentHashMap<>();

    public DockLayoutStore() {
        this(FabricLoader.getInstance().getConfigDir().resolve("glue-mcsx").resolve("dock"),
                DockLayoutStore::clientResource, Util.ioPool());
    }

    public DockLayoutStore(Path root, ResourceProvider resources, Executor writer) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.resources = Objects.requireNonNull(resources, "resources");
        this.writer = Objects.requireNonNull(writer, "writer");
    }

    /** Mirrors the resource path of a workspace under the config root, one directory per namespace. */
    public Path path(ResourceLocation workspace) {
        Objects.requireNonNull(workspace, "workspace");
        try {
            FileUtil.validatePath(workspace.getPath().split("/", -1));
            return FileUtil.createPathToResource(this.root.resolve(workspace.getNamespace()),
                    workspace.getPath(), EXTENSION);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Workspace id is not a usable dock config path: " + workspace,
                    exception);
        }
    }

    /**
     * Resolves the user layout, the Java default, the resource default, one tab group holding every
     * registered pane and finally an empty layout. A candidate that sanitizes to nothing — every one
     * of its panes is unknown to this workspace — is discarded so resolution continues instead of
     * opening a blank workspace the user cannot repopulate.
     */
    public DockLayout load(ResourceLocation workspace, boolean persistent, DockLayout javaDefault,
                           ResourceLocation resourceDefault, List<String> paneIds) {
        Set<String> knownPanes = validatedPaneIds(paneIds);
        if (persistent) {
            Path file = this.path(workspace);
            this.awaitPendingWrite(file);
            DockLayout user = sanitized(readSaved(file), knownPanes);
            if (user != null) return user;
        }
        DockLayout configured = sanitized(javaDefault, knownPanes);
        if (configured != null) return configured;

        ResourceLocation resource = resourceDefault != null ? resourceDefault : workspace;
        DockLayout shipped = sanitized(this.readResource(resource), knownPanes);
        if (shipped != null) return shipped;
        if (!paneIds.isEmpty()) return DockLayouts.layout(DockLayouts.tabs(paneIds));
        return DockLayouts.empty();
    }

    /** Writes the layout and waits for it, so a caller that reads the file next observes it. */
    public void save(ResourceLocation workspace, boolean persistent, DockLayout layout) {
        if (!persistent) return;

        this.saveLater(workspace, persistent, layout);
        this.flush(workspace);
    }

    /**
     * Encodes on the calling thread and writes off it, so a completed gesture never blocks the UI
     * thread on the config directory. Writes to one workspace file stay ordered.
     */
    public void saveLater(ResourceLocation workspace, boolean persistent, DockLayout layout) {
        if (!persistent) return;

        Path file = this.path(workspace);
        String document;
        try {
            document = DockLayoutCodec.write(layout);
        } catch (RuntimeException exception) {
            McsxDock.LOGGER.warn("Failed to encode dock layout {}", file, exception);
            return;
        }
        this.enqueue(file, () -> write(file, document));
    }

    public void reset(ResourceLocation workspace) {
        Path file = this.path(workspace);
        this.enqueue(file, () -> delete(file));
        this.flush(workspace);
    }

    /** Waits for writes already queued for this workspace. */
    public void flush(ResourceLocation workspace) {
        Path file = this.path(workspace);
        this.awaitPendingWrite(file);
    }

    private void enqueue(Path file, Runnable operation) {
        CompletableFuture<Void> queued = this.writes.compute(file, (ignoredKey, pending) -> {
            CompletableFuture<Void> previous = pending == null
                    ? CompletableFuture.completedFuture(null)
                    : pending.handle((ignoredResult, ignoredFailure) -> null);
            return previous.thenRunAsync(operation, this.writer);
        });
        queued.whenComplete((ignoredResult, ignoredFailure) -> this.writes.remove(file, queued));
    }

    private void awaitPendingWrite(Path file) {
        CompletableFuture<Void> pending;
        while ((pending = this.writes.get(file)) != null) {
            pending.join();
            this.writes.remove(file, pending);
        }
    }

    /**
     * Writes beside the target and lets {@link Util} swap the files, keeping the previous document
     * as the {@code _old} backup {@link #readSaved(Path)} falls back to.
     */
    private static void write(Path file, String document) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
            Files.writeString(temporary, document, StandardCharsets.UTF_8);
            if (!Util.safeReplaceOrMoveFile(file, temporary, backup(file), false)) {
                McsxDock.LOGGER.warn("Failed to replace dock layout {}", file);
            }
            Files.deleteIfExists(temporary);
        } catch (IOException | RuntimeException exception) {
            McsxDock.LOGGER.warn("Failed to save dock layout {}", file, exception);
            deleteQuietly(temporary, exception);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
            Files.deleteIfExists(backup(file));
        } catch (IOException exception) {
            McsxDock.LOGGER.warn("Failed to reset dock layout {}", file, exception);
        }
    }

    private static void deleteQuietly(Path file, Exception failure) {
        if (file == null) return;

        try {
            Files.deleteIfExists(file);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
    }

    private static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + BACKUP_SUFFIX);
    }

    /** Reads the saved layout, or the backup left by the last replacement when it is unusable. */
    private static DockLayout readSaved(Path file) {
        DockLayout saved = readFile(file, "user dock layout");
        return saved != null ? saved : readFile(backup(file), "dock layout backup");
    }

    private static DockLayout readFile(Path file, String description) {
        if (!Files.isRegularFile(file)) return null;

        try {
            return DockLayoutCodec.read(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException exception) {
            McsxDock.LOGGER.warn("Ignoring unreadable {} {}", description, file, exception);
            return null;
        }
    }

    private DockLayout readResource(ResourceLocation workspace) {
        ResourceLocation file = RESOURCES.idToFile(workspace);
        Optional<Resource> resource = this.resources.getResource(file);
        if (resource.isEmpty()) return null;

        try (Reader reader = resource.get().openAsReader()) {
            return DockLayoutCodec.read(IOUtils.toString(reader));
        } catch (IOException | RuntimeException exception) {
            McsxDock.LOGGER.warn("Ignoring unreadable default dock layout {}", file, exception);
            return null;
        }
    }

    /** Resolves against the loaded resource packs, or nothing before the client exists. */
    private static Optional<Resource> clientResource(ResourceLocation file) {
        return Minecraft.getInstance().getResourceManager().getResource(file);
    }

    private static DockLayout sanitized(DockLayout candidate, Set<String> knownPanes) {
        if (candidate == null) return null;

        DockLayout layout = DockOperations.sanitize(candidate, knownPanes);
        if (isEmpty(candidate)) return layout;
        return isEmpty(layout) ? null : layout;
    }

    private static boolean isEmpty(DockLayout layout) {
        return layout.tree() == null && layout.windows().isEmpty();
    }

    int pendingWriteCount() {
        return this.writes.size();
    }

    private static Set<String> validatedPaneIds(List<String> paneIds) {
        Objects.requireNonNull(paneIds, "paneIds");
        Set<String> knownPanes = new LinkedHashSet<>();
        for (String paneId : new ArrayList<>(paneIds)) {
            if (paneId == null || paneId.isBlank()) {
                throw new IllegalArgumentException("Pane ids cannot contain null or blank values");
            }
            if (!knownPanes.add(paneId)) throw new IllegalArgumentException("Duplicate pane id: " + paneId);
        }
        return Set.copyOf(knownPanes);
    }
}
