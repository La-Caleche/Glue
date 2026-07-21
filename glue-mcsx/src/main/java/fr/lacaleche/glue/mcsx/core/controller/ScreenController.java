package fr.lacaleche.glue.mcsx.core.controller;

import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.util.function.Supplier;

/**
 * Base class for the one Java controller that drives a {@code .mcsx} document. Reactive state is
 * declared as {@link Signal}/{@link Computed} fields; the binder reflects into them by name to
 * resolve {@code {name}} / {@code {{name}}}. The {@link #signal}/{@link #computed} factories are
 * conveniences so subclasses don't import the reactive package directly.
 */
public abstract class ScreenController {

    protected final <T> Signal<T> signal(T initial) {
        return new Signal<>(initial);
    }

    protected final <T> Computed<T> computed(Supplier<T> fn) {
        return new Computed<>(fn);
    }
}
