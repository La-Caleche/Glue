package fr.lacaleche.glue.testmod.gametest.mcsx;

import fr.lacaleche.glue.gametest.GameTest;
import icyllis.modernui.core.Core;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.View;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/** Shared step pump and assertions for game tests that drive a mounted MCSX fragment. */
public final class McsxGameTestSupport {

    private McsxGameTestSupport() {
    }

    /**
     * Adds a step that runs the action once on the ModernUI thread and holds the test until it
     * completes, so View state is only ever touched from the thread that owns it.
     */
    public static void ui(GameTest test, String description, Runnable action) {
        AtomicReference<CompletableFuture<Void>> pending = new AtomicReference<>();
        test.step(description, GameTest.DEFAULT_TIMEOUT, ctx -> {
            CompletableFuture<Void> future = pending.get();
            if (future == null) {
                future = new CompletableFuture<>();
                pending.set(future);
                CompletableFuture<Void> completion = future;
                Core.postOnUiThread(() -> {
                    try {
                        action.run();
                        completion.complete(null);
                    } catch (Throwable throwable) {
                        completion.completeExceptionally(throwable);
                    }
                });
            }
            if (!future.isDone()) return false;

            try {
                future.join();
            } catch (CompletionException wrapper) {
                // The step failed for the action's reason; the join wrapper only buries it a frame
                // deeper in the report.
                Throwable cause = wrapper.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw wrapper;
            }
            return true;
        });
    }

    /** Holds a step until a condition evaluated on the ModernUI thread becomes true. */
    public static void uiUntil(GameTest test, String description, BooleanSupplier condition) {
        AtomicReference<CompletableFuture<Boolean>> pending = new AtomicReference<>();
        test.step(description, GameTest.LONG_TIMEOUT, ctx -> {
            CompletableFuture<Boolean> future = pending.get();
            if (future == null) {
                future = new CompletableFuture<>();
                pending.set(future);
                CompletableFuture<Boolean> completion = future;
                Core.postOnUiThread(() -> {
                    try {
                        completion.complete(condition.getAsBoolean());
                    } catch (Throwable throwable) {
                        completion.completeExceptionally(throwable);
                    }
                });
            }
            if (!future.isDone()) return false;

            try {
                if (future.join()) return true;
            } catch (CompletionException wrapper) {
                Throwable cause = wrapper.getCause();
                if (cause instanceof RuntimeException runtime) throw runtime;
                if (cause instanceof Error error) throw error;
                throw wrapper;
            }
            pending.compareAndSet(future, null);
            return false;
        });
    }

    public static View tagged(Fragment fragment, String tag) {
        View view = fragment.requireView().findViewWithTag(tag);
        if (view == null) throw new IllegalStateException("Missing View tagged " + tag);
        return view;
    }

    public static <T extends View> T tagged(Fragment fragment, String tag, Class<T> type) {
        View view = fragment.requireView().findViewWithTag(tag);
        if (!type.isInstance(view)) {
            throw new IllegalStateException("Missing " + type.getSimpleName() + " tagged " + tag);
        }
        return type.cast(view);
    }

    public static <T extends View> T descendant(Fragment fragment, Class<T> type) {
        View view = fragment.requireView().findViewByPredicate(type::isInstance);
        if (view == null) throw new IllegalStateException("Missing " + type.getSimpleName());
        return type.cast(view);
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void requireSame(Object expected, Object actual, String role) {
        if (expected != actual) throw new IllegalStateException(role + " changed");
    }

    public static void requireEquals(Object expected, Object actual, String role) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(role + ": expected " + expected + ", got " + actual);
        }
    }
}
