package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

final class MappedValue<S, T> implements Value<T> {

    private final Value<S> source;
    private final Function<? super S, ? extends T> mapper;

    MappedValue(Value<S> source, Function<? super S, ? extends T> mapper) {
        this.source = source;
        this.mapper = mapper;
    }

    @Override
    public T get() {
        return this.mapper.apply(this.source.get());
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        return this.source.subscribe(new Consumer<>() {

            private T lastValue = MappedValue.this.get();

            @Override
            public void accept(S sourceValue) {
                T mappedValue = MappedValue.this.mapper.apply(sourceValue);
                if (Objects.equals(this.lastValue, mappedValue)) return;

                this.lastValue = mappedValue;
                listener.accept(mappedValue);
            }
        });
    }
}
