package fr.lacaleche.glue.mcsx.client.theme;

import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class McsxThemeLoaderTest {

    private static final PreparableReloadListener.PreparationBarrier IMMEDIATE_BARRIER =
            new PreparableReloadListener.PreparationBarrier() {
                @Override
                public <T> CompletableFuture<T> wait(T value) {
                    return CompletableFuture.completedFuture(value);
                }
            };

    @Test
    void reloadCompletesBeforeQueuedUiPublicationRuns() {
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        AtomicInteger publications = new AtomicInteger();
        McsxThemeLoader loader = new McsxThemeLoader(
                () -> true,
                queued::addLast,
                themes -> publications.incrementAndGet()
        );

        CompletableFuture<Void> reload = this.reload(loader);

        assertTrue(reload.isDone());
        assertFalse(reload.isCompletedExceptionally());
        assertEquals(1, queued.size());
        assertEquals(0, publications.get());
        queued.removeFirst().run();
        assertEquals(1, publications.get());
    }

    @Test
    void reloadFailsWhenUiPublicationCannotBeScheduled() {
        IllegalStateException failure = new IllegalStateException("dispatcher rejected task");
        McsxThemeLoader loader = new McsxThemeLoader(
                () -> true,
                task -> {
                    throw failure;
                },
                themes -> {
                }
        );

        CompletionException completion = assertThrows(CompletionException.class, this.reload(loader)::join);
        assertSame(failure, completion.getCause());
    }

    @Test
    void queuedPublicationFailureDoesNotRetroactivelyFailReload() {
        ArrayDeque<Runnable> queued = new ArrayDeque<>();
        AtomicInteger attempts = new AtomicInteger();
        McsxThemeLoader loader = new McsxThemeLoader(
                () -> true,
                queued::addLast,
                themes -> {
                    attempts.incrementAndGet();
                    throw new IllegalStateException("consumer failure");
                }
        );

        CompletableFuture<Void> reload = this.reload(loader);

        assertDoesNotThrow(reload::join);
        assertDoesNotThrow(queued.removeFirst()::run);
        assertEquals(1, attempts.get());
        assertFalse(reload.isCompletedExceptionally());
    }

    @Test
    void publishesSynchronouslyWithoutUiDispatcher() {
        AtomicInteger publications = new AtomicInteger();
        AtomicInteger dispatches = new AtomicInteger();
        McsxThemeLoader loader = new McsxThemeLoader(
                () -> false,
                task -> dispatches.incrementAndGet(),
                themes -> publications.incrementAndGet()
        );

        assertDoesNotThrow(this.reload(loader)::join);
        assertEquals(1, publications.get());
        assertEquals(0, dispatches.get());
    }

    private CompletableFuture<Void> reload(McsxThemeLoader loader) {
        return loader.reload(
                IMMEDIATE_BARRIER,
                new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of()),
                Runnable::run,
                Runnable::run
        );
    }
}
