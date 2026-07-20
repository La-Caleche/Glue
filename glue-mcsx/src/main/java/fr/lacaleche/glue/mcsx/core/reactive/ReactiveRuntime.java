package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.ArrayDeque;

/**
 * Dependency-tracking runtime: a single stack of the currently-evaluating {@link Observer}. When a
 * {@link Source} is read inside an {@link Effect}/{@link Computed} body, the edge is recorded both
 * ways — source → observer (so a write can notify it) and observer → source (so it can unsubscribe
 * on its next run). Synchronous, single-threaded, no scheduler.
 *
 * <p>{@link Reactive#untracked} pushes {@link #UNTRACKED} onto this same stack rather than flipping a
 * flag: an observer created <em>inside</em> an untracked region pushes itself above the sentinel and
 * must still track its own reads. Only the top frame decides attribution.
 */
final class ReactiveRuntime {

    /** Occupies the top frame while an untracked region runs; reads under it are attributed to nobody. */
    private static final Object UNTRACKED = new Object();

    private static final ArrayDeque<Object> STACK = new ArrayDeque<>();

    private ReactiveRuntime() {
    }

    static void onRead(Source source) {
        if (STACK.peek() instanceof Observer active) {
            source.addObserver(active);
            active.onDependency(source);
        }
    }

    static void push(Observer observer) {
        STACK.push(observer);
    }

    static void pushUntracked() {
        STACK.push(UNTRACKED);
    }

    static void pop() {
        STACK.pop();
    }
}
