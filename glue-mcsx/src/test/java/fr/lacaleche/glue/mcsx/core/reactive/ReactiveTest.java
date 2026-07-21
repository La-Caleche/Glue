package fr.lacaleche.glue.mcsx.core.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveTest {

    @Test
    void effectRunsOnceOnCreationThenOnEachWrite() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();
        Effect.of(() -> {
            count.get();
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        count.set(1);
        count.set(2);
        assertEquals(3, runs.get());
    }

    @Test
    void equalWriteIsANoOp() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();
        Effect.of(() -> {
            count.get();
            runs.incrementAndGet();
        });
        count.set(0);
        assertEquals(1, runs.get());
    }

    @Test
    void computedIsLazyAndReactive() {
        Signal<Integer> count = new Signal<>(2);
        AtomicInteger recomputes = new AtomicInteger();
        Computed<Integer> doubled = new Computed<>(() -> {
            recomputes.incrementAndGet();
            return count.get() * 2;
        });
        assertEquals(0, recomputes.get(), "computed must not evaluate until first read");
        assertEquals(4, doubled.get());
        assertEquals(4, doubled.get());
        assertEquals(1, recomputes.get(), "cached: no recompute on a second read");
        count.set(5);
        assertEquals(10, doubled.get());
        assertEquals(2, recomputes.get());
    }

    @Test
    void computedRetracksConditionalDependencies() {
        Signal<Boolean> useA = new Signal<>(true);
        Signal<Integer> a = new Signal<>(1);
        Signal<Integer> b = new Signal<>(100);
        Computed<Integer> pick = new Computed<>(() -> useA.get() ? a.get() : b.get());

        List<Integer> seen = new ArrayList<>();
        Effect.of(() -> seen.add(pick.get()));
        assertEquals(List.of(1), seen);

        // while on branch A, writing b must NOT re-run the effect
        b.set(200);
        assertEquals(List.of(1), seen);

        // writing a does
        a.set(2);
        assertEquals(List.of(1, 2), seen);

        // switch to branch B; now a is no longer a dependency, b is
        useA.set(false);
        assertEquals(List.of(1, 2, 200), seen);
        a.set(3);
        assertEquals(List.of(1, 2, 200), seen);
        b.set(300);
        assertEquals(List.of(1, 2, 200, 300), seen);
    }

    @Test
    void disposedEffectStopsReacting() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();
        Effect effect = Effect.of(() -> {
            count.get();
            runs.incrementAndGet();
        });
        count.set(1);
        assertEquals(2, runs.get());
        effect.dispose();
        count.set(2);
        assertEquals(2, runs.get());
    }

    @Test
    void closedComputedDetachesFromItsSources() {
        Signal<Integer> count = new Signal<>(1);
        Computed<Integer> doubled = new Computed<>(() -> count.get() * 2);
        List<Integer> seen = new ArrayList<>();
        Effect effect = Effect.of(() -> seen.add(doubled.get()));

        doubled.close();
        count.set(2);

        assertEquals(List.of(2), seen);
        effect.dispose();
    }

    @Test
    void disposeRunsTeardownCallbackOnce() {
        Signal<Integer> s = new Signal<>(0);
        AtomicInteger teardowns = new AtomicInteger();
        Effect effect = Effect.of(s::get);
        effect.onDispose(teardowns::incrementAndGet);
        effect.dispose();
        effect.dispose();
        assertEquals(1, teardowns.get());
    }

    @Test
    void effectWritingItsOwnDependencyThrows() {
        Signal<Integer> count = new Signal<>(0);
        assertThrows(IllegalStateException.class,
                () -> Effect.of(() -> count.set(count.get() + 1)));

        assertDoesNotThrow(() -> count.set(2));
    }

    @Test
    void failedInitialEffectDetachesFromItsSources() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> Effect.of(() -> {
            count.get();
            runs.incrementAndGet();
            throw new IllegalStateException("failed");
        }));

        count.set(1);
        assertEquals(1, runs.get());
    }

    @Test
    void failedInitialEffectPreservesTeardownFailureAsSuppressed() {
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> Effect.of(
                        () -> {
                            throw new IllegalStateException("body");
                        },
                        () -> {
                            throw new IllegalArgumentException("teardown");
                        }));

        assertEquals("body", failure.getMessage());
        assertEquals("teardown", failure.getSuppressed()[0].getMessage());
    }

    @Test
    void diamondConvergesToCorrectValue() {
        Signal<Integer> a = new Signal<>(1);
        Computed<Integer> b = new Computed<>(() -> a.get() * 2);
        List<String> seen = new ArrayList<>();
        Effect.of(() -> seen.add(a.get() + "/" + b.get()));
        assertEquals("1/2", seen.get(seen.size() - 1));
        a.set(3);
        // may run more than once (no glitch avoidance), but the LAST run observes the settled graph
        assertEquals("3/6", seen.get(seen.size() - 1));
        assertTrue(seen.size() >= 2);
    }

    /** A dependency change whose recomputed value is equal must not re-fire downstream effects. */
    @Test
    void computedShortCircuitsEqualRecomputes() {
        Signal<String> name = new Signal<>("a");
        Computed<Boolean> nonEmpty = new Computed<>(() -> !name.get().isEmpty());
        AtomicInteger runs = new AtomicInteger();
        Effect.of(() -> {
            nonEmpty.get();
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        name.set("ab");
        name.set("abc");
        assertEquals(1, runs.get(), "true -> true recomputes must not re-run the effect");
        name.set("");
        assertEquals(2, runs.get(), "a real change still propagates");
    }

    /** While unobserved, an invalidated computed must stay lazy — no eager recompute. */
    @Test
    void unobservedComputedStaysLazyOnInvalidate() {
        Signal<Integer> count = new Signal<>(1);
        AtomicInteger recomputes = new AtomicInteger();
        Computed<Integer> doubled = new Computed<>(() -> {
            recomputes.incrementAndGet();
            return count.get() * 2;
        });
        assertEquals(2, doubled.get());
        assertEquals(1, recomputes.get());
        count.set(5);
        count.set(6);
        assertEquals(1, recomputes.get(), "no observers: invalidation must not recompute");
        assertEquals(12, doubled.get());
        assertEquals(2, recomputes.get());
    }

    @Test
    void untrackedReadDoesNotSubscribeTheEnclosingEffect() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();
        Effect.of(() -> {
            Reactive.untracked(count::get);
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        count.set(1);
        assertEquals(1, runs.get(), "an untracked read must not be a dependency");
    }

    /**
     * THE invariant that forces a stack sentinel over a boolean flag: an observer created inside an
     * untracked region pushes itself ABOVE the sentinel, so it must keep tracking its own reads. This
     * is what lets the {@code <overlay>} panel's restyle effect stay live inside the gate's region.
     */
    @Test
    void effectCreatedInsideUntrackedStillTracksItsOwnReads() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger outerRuns = new AtomicInteger();
        AtomicInteger innerRuns = new AtomicInteger();

        Effect.of(() -> {
            outerRuns.incrementAndGet();
            Reactive.untracked(() -> Effect.of(() -> {
                count.get();
                innerRuns.incrementAndGet();
            }));
        });
        assertEquals(1, innerRuns.get());

        count.set(1);
        assertEquals(2, innerRuns.get(), "the nested effect owns its reads");
        assertEquals(1, outerRuns.get(), "the enclosing effect must not have subscribed");
    }

    /** The mirror for {@link Computed}: first read under the sentinel still re-tracks its own sources. */
    @Test
    void computedFirstReadInsideUntrackedStaysReactiveForALaterObserver() {
        Signal<Integer> count = new Signal<>(1);
        Computed<Integer> doubled = new Computed<>(() -> count.get() * 2);

        AtomicInteger gateRuns = new AtomicInteger();
        Effect.of(() -> {
            gateRuns.incrementAndGet();
            assertEquals(2, Reactive.untracked(doubled::get));
        });
        count.set(5);
        assertEquals(1, gateRuns.get(), "reading a computed untracked must not subscribe the gate");

        List<Integer> seen = new ArrayList<>();
        Effect.of(() -> seen.add(doubled.get()));
        assertEquals(List.of(10), seen, "the computed re-tracked its own sources under the sentinel");
        count.set(6);
        assertEquals(List.of(10, 12), seen);
    }

    /**
     * {@code <for>} throws "must resolve to a List" from inside its untracked rebuild. The finally-pop
     * must leave the stack balanced, or every later read in the app would be silently untracked.
     */
    @Test
    void untrackedRestoresTrackingAfterAThrow() {
        Signal<Integer> count = new Signal<>(0);
        AtomicInteger runs = new AtomicInteger();
        Effect.of(() -> {
            try {
                Reactive.untracked(() -> {
                    throw new IllegalStateException("rebuild failed");
                });
            } catch (IllegalStateException ignored) {
                // the gate's rebuild blew up; tracking must be intact for the reads that follow
            }
            count.get();
            runs.incrementAndGet();
        });
        assertEquals(1, runs.get());
        count.set(1);
        assertEquals(2, runs.get(), "the stack must be balanced after a throw inside the region");
    }

    @Test
    void untrackedOutsideAnyObserverIsANoOp() {
        Signal<Integer> count = new Signal<>(3);
        assertEquals(3, Reactive.untracked(count::get));
        assertEquals(7, Reactive.untracked(() -> 7));
    }

    /**
     * The defect itself, reproduced headlessly: a {@code <for>} gate reads its list, then builds each
     * row. A row's build path reads that row's own signal (via resolveStyle / a {@code <variants on>}
     * prop). Without the untracked region the gate subscribes to every row, so ticking row 1 rebuilds
     * all of them — and each rebuild mints fresh {@code <state>} signals, wiping the siblings.
     */
    @Test
    void gateRebuildDoesNotSubscribeToWhatItBuilds() {
        record Row(Signal<Boolean> done) {
        }
        Row first = new Row(new Signal<>(false));
        Row second = new Row(new Signal<>(false));
        Signal<List<Row>> list = new Signal<>(List.of(first, second));

        AtomicInteger gateRuns = new AtomicInteger();
        Effect.of(() -> {
            gateRuns.incrementAndGet();
            List<Row> rows = list.get();
            Reactive.untracked(() -> {
                for (Row row : rows) {
                    row.done().get();
                }
            });
        });
        assertEquals(1, gateRuns.get());

        second.done().set(true);
        assertEquals(1, gateRuns.get(), "a row's own signal must not re-run the loop gate");

        list.set(List.of(first));
        assertEquals(2, gateRuns.get(), "the list itself is still a real dependency");
    }

}
