package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * A derived value: both a {@link Source} (it can be read) and an {@link Observer} (it re-evaluates
 * when its dependencies change). Dependencies are re-tracked on every recompute, so conditional
 * reads narrow/widen the subscription set automatically.
 *
 * <p>Invalidation is equality-gated: while observed, a dependency change recomputes immediately
 * and propagates only when the new value differs (per {@link Objects#equals}) — the same
 * short-circuit {@link Signal#set} applies one level down, so {@code isValid}-style computeds
 * don't re-fire downstream effects on every keystroke. While unobserved, invalidation just marks
 * stale and recompute stays lazy (on the next {@link #get()}). Consequence of the eager path: an
 * exception thrown by the compute function surfaces from the signal <em>write</em> that triggered
 * it, not from the next read.
 */
public final class Computed<T> extends Observable implements Source, Observer, AutoCloseable {

    private final Supplier<T> fn;
    private T value;
    private boolean stale = true;
    private final Set<Source> sources = new LinkedHashSet<>();

    public Computed(Supplier<T> fn) {
        this.fn = fn;
    }

    public T get() {
        ReactiveRuntime.onRead(this);
        if (stale) {
            recompute();
        }
        return value;
    }

    private void recompute() {
        detachSources();
        ReactiveRuntime.push(this);
        try {
            value = fn.get();
        } finally {
            ReactiveRuntime.pop();
        }
        stale = false;
    }

    private void detachSources() {
        for (Source source : sources) {
            source.removeObserver(this);
        }
        sources.clear();
    }

    @Override
    public void invalidate() {
        if (stale) {
            return;
        }
        stale = true;
        if (!hasObservers()) {
            return;
        }
        T old = value;
        recompute();
        if (Objects.equals(old, value)) {
            return;
        }
        notifyObservers();
    }

    @Override
    public void onDependency(Source source) {
        sources.add(source);
    }

    /** Detaches this derived value from its sources when its owner is discarded. */
    @Override
    public void close() {
        detachSources();
        clearObservers();
        stale = true;
        value = null;
    }
}
