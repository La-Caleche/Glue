package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.function.Supplier;

/**
 * Reactive-graph operations that belong to no single node.
 *
 * <p>{@code untracked} exists for the structural gates ({@code <if>}/{@code <for>}/{@code <overlay>}):
 * they rebuild a whole subtree inside their effect body, so every style/attribute read on that build
 * path would otherwise become a dependency of the gate — ticking one row's checkbox would rebuild the
 * whole list and reset every sibling's {@code <state>}.
 */
public final class Reactive {

    private Reactive() {
    }

    /**
     * Runs {@code body} with its {@link Signal}/{@link Computed} reads attributed to nobody. An
     * observer created <em>inside</em> the region still tracks its own reads: the region suppresses
     * attribution to the <em>enclosing</em> {@link Effect}/{@link Computed}, not tracking as such.
     */
    public static void untracked(Runnable body) {
        ReactiveRuntime.pushUntracked();
        try {
            body.run();
        } finally {
            ReactiveRuntime.pop();
        }
    }

    /** {@link #untracked(Runnable)} for a body that produces a value. */
    public static <T> T untracked(Supplier<T> body) {
        ReactiveRuntime.pushUntracked();
        try {
            return body.get();
        } finally {
            ReactiveRuntime.pop();
        }
    }
}
