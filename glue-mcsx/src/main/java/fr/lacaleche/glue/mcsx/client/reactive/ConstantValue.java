package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.Objects;
import java.util.function.Consumer;

final class ConstantValue<T> implements Value<T> {

    private final T value;

    ConstantValue(T value) {
        this.value = value;
    }

    @Override
    public T get() {
        return this.value;
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        UiThreadGuard.check();
        Objects.requireNonNull(listener, "listener");
        return () -> {
        };
    }
}
