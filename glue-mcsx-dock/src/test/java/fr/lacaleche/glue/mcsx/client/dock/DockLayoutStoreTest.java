package fr.lacaleche.glue.mcsx.client.dock;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.client.dock.internal.persistence.DockLayoutStore;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockTabs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockLayoutStoreTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void derivesNamespaceAndNestedPathUnderTheConfiguredRoot() {
        DockLayoutStore store = this.store(this.temporaryDirectory);

        assertEquals(
                this.temporaryDirectory.toAbsolutePath().resolve("example/tools/editor.json").normalize(),
                store.path(id("example", "tools/editor"))
        );
    }

    @Test
    void resolutionUsesUserThenJavaThenResourceThenRegisteredPanes() {
        DockLayoutStore store = this.storeWithResources(this.temporaryDirectory.resolve("config"), Map.of(
                id("example", "mcsx/dock/workspace.json"),
                DockLayoutCodec.write(DockLayouts.layout(DockLayouts.tabs("resource")))
        ));
        ResourceLocation workspace = id("example", "workspace");
        DockLayout javaDefault = DockLayouts.layout(DockLayouts.tabs("java"));

        DockLayout javaResolved = store.load(workspace, true, javaDefault, null,
                List.of("user", "java", "resource"));
        assertEquals("java", ((DockTabs) javaResolved.tree()).active());

        DockLayout resourceResolved = store.load(workspace, true, null, null,
                List.of("user", "java", "resource"));
        assertEquals("resource", ((DockTabs) resourceResolved.tree()).active());

        store.save(workspace, true, DockLayouts.layout(DockLayouts.tabs("user")));
        DockLayout userResolved = store.load(workspace, true, javaDefault, null,
                List.of("user", "java", "resource"));
        assertEquals("user", ((DockTabs) userResolved.tree()).active());

        DockLayout fallback = store.load(id("example", "missing"), false, null, null,
                List.of("java", "resource"));
        assertEquals(List.of("java", "resource"), ((DockTabs) fallback.tree()).tabs());
    }

    @Test
    void malformedUserFileFallsBackSanitizesAndResetDoesNotRewrite() throws IOException {
        DockLayoutStore store = this.store(this.temporaryDirectory);
        ResourceLocation workspace = id("example", "fallback");
        Path file = store.path(workspace);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not json", StandardCharsets.UTF_8);
        DockLayout javaDefault = DockLayouts.layout(DockLayouts.tabs("known", "removed"));

        DockLayout resolved = store.load(workspace, true, javaDefault, null, List.of("known"));
        assertEquals(List.of("known"), ((DockTabs) resolved.tree()).tabs());

        store.reset(workspace);
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(backup(file)));
    }

    @Test
    void saveReplacesTheDocumentAndLeavesOnlyItsBackupBehind() throws IOException {
        DockLayoutStore store = this.store(this.temporaryDirectory);
        ResourceLocation workspace = id("example", "atomic");
        DockLayout first = DockLayouts.layout(DockLayouts.tabs("first"));
        DockLayout second = DockLayouts.layout(DockLayouts.tabs("second"));

        store.save(workspace, true, first);
        store.save(workspace, true, second);

        Path file = store.path(workspace);
        assertEquals(second, DockLayoutCodec.read(Files.readString(file, StandardCharsets.UTF_8)));
        assertEquals(first, DockLayoutCodec.read(Files.readString(backup(file), StandardCharsets.UTF_8)));
        try (Stream<Path> written = Files.list(file.getParent())) {
            assertEquals(Set.of(file, backup(file)), written.collect(Collectors.toSet()));
        }
    }

    @Test
    void anUnreadableSavedLayoutFallsBackToTheBackupOfTheLastSave() throws IOException {
        DockLayoutStore store = this.store(this.temporaryDirectory);
        ResourceLocation workspace = id("example", "recovered");
        store.save(workspace, true, DockLayouts.layout(DockLayouts.tabs("viewport")));
        store.save(workspace, true, DockLayouts.layout(DockLayouts.tabs("console")));
        Path file = store.path(workspace);
        Files.writeString(file, "truncated {", StandardCharsets.UTF_8);

        DockLayout resolved = store.load(workspace, true, null, null, List.of("viewport", "console"));

        assertEquals(List.of("viewport"), ((DockTabs) resolved.tree()).tabs());
    }

    @Test
    void aUserLayoutRetainingNoKnownPaneFallsBackToTheConfiguredDefault() {
        DockLayoutStore store = this.store(this.temporaryDirectory);
        ResourceLocation workspace = id("example", "renamed");
        store.save(workspace, true, DockLayouts.layout(DockLayouts.tabs("legacy_viewport")));
        DockLayout javaDefault = DockLayouts.layout(DockLayouts.tabs("viewport"));

        DockLayout resolved = store.load(workspace, true, javaDefault, null, List.of("viewport"));

        assertEquals(List.of("viewport"), ((DockTabs) resolved.tree()).tabs());
    }

    @Test
    void explicitEmptyCandidatesRemainValidAtEveryResolutionLevel() {
        ResourceLocation workspace = id("example", "empty");
        DockLayout empty = DockLayouts.empty();
        DockLayout fallback = DockLayouts.layout(DockLayouts.tabs("viewport"));
        DockLayoutStore userStore = this.store(this.temporaryDirectory.resolve("user"));
        userStore.save(workspace, true, empty);

        assertEquals(empty, userStore.load(workspace, true, fallback, null, List.of("viewport")));

        DockLayoutStore javaStore = this.store(this.temporaryDirectory.resolve("java"));
        assertEquals(empty, javaStore.load(workspace, false, empty, null, List.of("viewport")));

        DockLayoutStore resourceStore = this.storeWithResources(this.temporaryDirectory.resolve("resource"), Map.of(
                id("example", "mcsx/dock/empty.json"), DockLayoutCodec.write(empty)
        ));
        assertEquals(empty, resourceStore.load(workspace, false, null, null, List.of("viewport")));
    }

    @Test
    void deferredSavesDoNotTouchTheConfigDirectoryOnTheCallingThread() {
        Deque<Runnable> queued = new ArrayDeque<>();
        DockLayoutStore store = new DockLayoutStore(this.temporaryDirectory, ResourceProvider.EMPTY, queued::add);
        ResourceLocation workspace = id("example", "deferred");

        store.saveLater(workspace, true, DockLayouts.layout(DockLayouts.tabs("viewport")));
        assertFalse(Files.exists(store.path(workspace)));

        queued.removeFirst().run();
        assertTrue(Files.isRegularFile(store.path(workspace)));
    }

    private DockLayoutStore store(Path root) {
        return new DockLayoutStore(root, ResourceProvider.EMPTY, Runnable::run);
    }

    private DockLayoutStore storeWithResources(Path root, Map<ResourceLocation, String> documents) {
        return new DockLayoutStore(root, resources(documents), Runnable::run);
    }

    private static ResourceProvider resources(Map<ResourceLocation, String> documents) {
        return file -> Optional.ofNullable(documents.get(file)).map(DockLayoutStoreTest::resource);
    }

    /** The store only opens the stream, so the pack owning the resource is irrelevant here. */
    private static Resource resource(String document) {
        return new Resource(null, () -> new ByteArrayInputStream(document.getBytes(StandardCharsets.UTF_8)));
    }

    private static Path backup(Path file) {
        return file.resolveSibling(file.getFileName() + "_old");
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }
}
