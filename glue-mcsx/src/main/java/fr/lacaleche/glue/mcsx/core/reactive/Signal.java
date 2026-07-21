package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.Objects;
import java.util.function.UnaryOperator;

/**
 * The mutable root of the reactive graph. Reading tracks a dependency on the active observer;
 * writing an unequal value synchronously invalidates every observer that read it. Equal writes
 * (by {@link Objects#equals}) are no-ops and do not propagate.
 */
public final class Signal<T> extends Observable implements Source {

    private T value;

    public Signal(T initial) {
        this.value = initial;
    }

    public T get() {
        ReactiveRuntime.onRead(this);
        return value;
    }

    public void set(T newValue) {
        if (Objects.equals(value, newValue)) {
            return;
        }
        value = newValue;
        notifyObservers();
    }

    public void update(UnaryOperator<T> f) {
        set(f.apply(value));
    }
}
