package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A UI-thread-confined value whose subscriptions observe later changes without an initial emission.
 */
public interface Value<T> {

    T get();

    Subscription subscribe(Consumer<? super T> listener);

    /**
     * Combines two sources: the current result is always readable, each distinct source is
     * subscribed once while the combined value has listeners, equal combined results are suppressed,
     * and every published result is a snapshot in which all sources are current — so the same source
     * may occupy several operand positions, and operands sharing an upstream are never read one
     * change apart.
     */
    static <A, B, R> Value<R> combine(
            Value<? extends A> first,
            Value<? extends B> second,
            BiFunction<? super A, ? super B, ? extends R> combiner
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(combiner, "combiner");
        return new CombinedValue<>(
                List.<Value<?>>of(first, second),
                () -> combiner.apply(first.get(), second.get())
        );
    }

    /** The three-source form of {@link #combine(Value, Value, BiFunction)}, with the same contract. */
    static <A, B, C, R> Value<R> combine(
            Value<? extends A> first,
            Value<? extends B> second,
            Value<? extends C> third,
            TriFunction<? super A, ? super B, ? super C, ? extends R> combiner
    ) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        Objects.requireNonNull(third, "third");
        Objects.requireNonNull(combiner, "combiner");
        return new CombinedValue<>(
                List.<Value<?>>of(first, second, third),
                () -> combiner.apply(first.get(), second.get(), third.get())
        );
    }

    default <R> Value<R> map(Function<? super T, ? extends R> mapper) {
        return new MappedValue<>(this, Objects.requireNonNull(mapper, "mapper"));
    }

    /** A three-source analogue of {@link BiFunction} for {@link #combine(Value, Value, Value, TriFunction)}. */
    @FunctionalInterface
    interface TriFunction<A, B, C, R> {

        R apply(A first, B second, C third);
    }
}
