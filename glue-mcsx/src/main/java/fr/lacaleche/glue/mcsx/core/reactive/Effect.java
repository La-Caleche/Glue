package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The bridge from the reactive graph to side effects (e.g. mutating a View property). An effect runs
 * its body immediately on creation (establishing dependencies) and synchronously re-runs whenever any
 * dependency changes. The {@code running} guard forbids an effect from writing a signal it reads,
 * which would loop forever.
 */
public final class Effect implements Observer {

    private final Runnable body;
    private Set<Source> sources = new LinkedHashSet<>();
    private Set<Source> previousSources = new LinkedHashSet<>();
    private Runnable onDispose;
    private boolean running;
    private boolean disposed;

    private Effect(Runnable body) {
        this.body = body;
    }

    public static Effect of(Runnable body) {
        return of(body, null);
    }

    /** Creates an effect whose teardown is installed before its immediate initial run. */
    public static Effect of(Runnable body, Runnable onDispose) {
        Effect effect = new Effect(body);
        effect.onDispose = onDispose;
        try {
            effect.run();
        } catch (RuntimeException | Error failure) {
            try {
                effect.dispose();
            } catch (RuntimeException | Error teardownFailure) {
                failure.addSuppressed(teardownFailure);
            }
            throw failure;
        }
        return effect;
    }

    @Override
    public void invalidate() {
        if (disposed) {
            return;
        }
        if (running) {
            throw new IllegalStateException(
                    "effect invalidated while running — an effect must not write its own dependencies");
        }
        run();
    }

    /**
     * Whether the last run recorded a dependency. An effect that recorded none can never be
     * invalidated, so an owner that only holds it to re-run it may drop it instead of retaining and
     * later disposing it — the majority case for the per-child layout effects of a static subtree.
     */
    public boolean hasSources() {
        return !sources.isEmpty();
    }

    /** Registers a teardown callback (e.g. {@code <if>}/{@code <for>} disposing nested effects). */
    public void onDispose(Runnable callback) {
        this.onDispose = callback;
    }

    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        for (Source source : sources) {
            source.removeObserver(this);
        }
        sources.clear();
        if (onDispose != null) {
            Runnable callback = onDispose;
            onDispose = null;
            callback.run();
        }
    }

    @Override
    public void onDependency(Source source) {
        sources.add(source);
    }

    private void run() {
        // Diff instead of unsubscribe-all/resubscribe-all: dependencies are overwhelmingly stable
        // across runs, and a retained source's re-add during the run is a no-op set insert. Only
        // sources the body stopped reading are unsubscribed, after the run.
        Set<Source> retired = sources;
        sources = previousSources;
        previousSources = retired;
        sources.clear();
        running = true;
        ReactiveRuntime.push(this);
        try {
            body.run();
        } finally {
            ReactiveRuntime.pop();
            running = false;
            for (Source source : previousSources) {
                if (!sources.contains(source)) {
                    source.removeObserver(this);
                }
            }
            previousSources.clear();
        }
    }
}
