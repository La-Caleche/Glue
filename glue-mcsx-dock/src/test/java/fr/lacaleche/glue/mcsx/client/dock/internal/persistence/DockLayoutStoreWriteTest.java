package fr.lacaleche.glue.mcsx.client.dock.internal.persistence;

import fr.lacaleche.glue.mcsx.client.dock.internal.layout.DockLayoutCodec;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayout;
import fr.lacaleche.glue.mcsx.client.dock.layout.DockLayouts;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockLayoutStoreWriteTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void deferredWritesToOneWorkspaceRemainOrdered() throws IOException {
        Deque<Runnable> queued = new ArrayDeque<>();
        DockLayoutStore store = this.store(queued);
        ResourceLocation workspace = id("ordered");
        DockLayout first = DockLayouts.layout(DockLayouts.tabs("first"));
        DockLayout second = DockLayouts.layout(DockLayouts.tabs("second"));

        store.saveLater(workspace, true, first);
        store.saveLater(workspace, true, second);

        assertEquals(1, queued.size());
        queued.removeFirst().run();
        assertEquals(1, queued.size());
        queued.removeFirst().run();
        store.flush(workspace);

        String document = Files.readString(store.path(workspace), StandardCharsets.UTF_8);
        assertEquals(second, DockLayoutCodec.read(document));
    }

    @Test
    void completedWritesRemoveTheirPendingEntry() {
        Deque<Runnable> queued = new ArrayDeque<>();
        DockLayoutStore store = this.store(queued);
        ResourceLocation workspace = id("cleanup");

        store.saveLater(workspace, true, DockLayouts.empty());
        assertEquals(1, store.pendingWriteCount());

        queued.removeFirst().run();
        assertEquals(0, store.pendingWriteCount());
    }

    @Test
    void flushWaitsForThePendingWrite() throws Exception {
        Deque<Runnable> queued = new ArrayDeque<>();
        DockLayoutStore store = this.store(queued);
        ResourceLocation workspace = id("flush");
        DockLayout finalLayout = DockLayouts.layout(DockLayouts.tabs("final"));
        store.saveLater(workspace, true, finalLayout);

        CompletableFuture<Void> flushing = CompletableFuture.runAsync(() -> store.flush(workspace));
        assertFalse(flushing.isDone());

        queued.removeFirst().run();
        flushing.get(5, TimeUnit.SECONDS);

        assertTrue(Files.isRegularFile(store.path(workspace)));
        assertEquals(0, store.pendingWriteCount());
    }

    private DockLayoutStore store(Deque<Runnable> queued) {
        return new DockLayoutStore(this.temporaryDirectory, ResourceProvider.EMPTY, queued::add);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("test", path);
    }
}
