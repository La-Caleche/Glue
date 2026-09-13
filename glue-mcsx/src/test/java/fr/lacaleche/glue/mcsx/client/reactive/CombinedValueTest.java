package fr.lacaleche.glue.mcsx.client.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class CombinedValueTest {

    @Test
    void combinesCurrentValuesWithoutInitialEmission() {
        Signal<Integer> first = Signal.of(2);
        Signal<Integer> second = Signal.of(3);
        Value<Integer> sum = Value.combine(first, second, Integer::sum);
        List<Integer> values = new ArrayList<>();

        sum.subscribe(values::add);
        assertEquals(5, sum.get());
        assertEquals(List.of(), values);

        first.set(4);
        second.set(5);
        assertEquals(List.of(7, 9), values);
    }

    @Test
    void suppressesEqualCombinedResults() {
        Signal<Integer> first = Signal.of(1);
        Signal<Integer> second = Signal.of(1);
        Value<Boolean> equal = Value.combine(first, second, Objects::equals);
        List<Boolean> values = new ArrayList<>();
        equal.subscribe(values::add);

        first.set(2);
        second.set(2);
        first.set(3);

        assertEquals(List.of(false, true, false), values);
    }

    @Test
    void crossSourceWritesReachEveryListenerInFifoOrder() {
        Signal<Integer> first = Signal.of(0);
        Signal<Integer> second = Signal.of(0);
        Value<String> combined = Value.combine(first, second, (a, b) -> a + ":" + b);
        List<String> events = new ArrayList<>();
        combined.subscribe(value -> {
            events.add("first:" + value);
            if (value.equals("1:0")) {
                second.set(1);
            }
        });
        combined.subscribe(value -> events.add("second:" + value));

        first.set(1);

        assertEquals(List.of(
                "first:1:0",
                "second:1:0",
                "first:1:1",
                "second:1:1"
        ), events);
    }

    @Test
    void listenerFailureDoesNotDiscardOtherOrReentrantNotifications() {
        Signal<Integer> first = Signal.of(0);
        Signal<Integer> second = Signal.of(0);
        Value<Integer> sum = Value.combine(first, second, Integer::sum);
        List<Integer> values = new ArrayList<>();
        sum.subscribe(value -> {
            if (value == 1) {
                second.set(1);
                throw new IllegalStateException("broken listener");
            }
        });
        sum.subscribe(values::add);

        assertThrows(IllegalStateException.class, () -> first.set(1));

        assertEquals(List.of(1, 2), values);
    }

    @Test
    void firstAndLastListenersOwnOneSourceGeneration() {
        TrackingValue<Integer> first = new TrackingValue<>(1);
        TrackingValue<Integer> second = new TrackingValue<>(2);
        Value<Integer> sum = Value.combine(first, second, Integer::sum);

        Subscription firstListener = sum.subscribe(value -> {
        });
        Subscription secondListener = sum.subscribe(value -> {
        });
        assertEquals(1, first.subscribeCount);
        assertEquals(1, second.subscribeCount);

        firstListener.close();
        assertEquals(0, first.closeCount);
        secondListener.close();
        assertEquals(1, first.closeCount);
        assertEquals(1, second.closeCount);

        sum.subscribe(value -> {
        }).close();
        assertEquals(2, first.subscribeCount);
        assertEquals(2, second.subscribeCount);
    }

    @Test
    void secondSubscriptionFailureRollsBackFirstAndCanRetry() {
        TrackingValue<Integer> first = new TrackingValue<>(1);
        TrackingValue<Integer> second = new TrackingValue<>(2);
        IllegalStateException failure = new IllegalStateException("subscription failure");
        second.subscribeFailure = failure;
        Value<Integer> sum = Value.combine(first, second, Integer::sum);

        IllegalStateException thrown = assertThrows(
                IllegalStateException.class,
                () -> sum.subscribe(value -> {
                })
        );
        assertSame(failure, thrown);
        assertEquals(1, first.closeCount);

        second.subscribeFailure = null;
        sum.subscribe(value -> {
        }).close();
        assertEquals(2, first.subscribeCount);
        assertEquals(2, second.subscribeCount);
    }

    @Test
    void ternaryCombineTracksEverySourceAndSuppressesEqualResults() {
        Signal<Integer> first = Signal.of(1);
        Signal<Integer> second = Signal.of(2);
        Signal<Integer> third = Signal.of(3);
        Value<Integer> highest = Value.combine(first, second, third, (a, b, c) ->
                Math.max(a, Math.max(b, c)));
        List<Integer> values = new ArrayList<>();
        highest.subscribe(values::add);

        assertEquals(3, highest.get());
        assertEquals(List.of(), values);

        first.set(2);
        third.set(5);
        second.set(4);
        third.set(1);

        assertEquals(4, highest.get());
        assertEquals(List.of(5, 4), values);
    }

    @Test
    void ternaryListenersOwnOneGenerationAcrossAllThreeSources() {
        TrackingValue<Integer> first = new TrackingValue<>(1);
        TrackingValue<Integer> second = new TrackingValue<>(2);
        TrackingValue<Integer> third = new TrackingValue<>(3);
        Value<Integer> sum = Value.combine(first, second, third, (a, b, c) -> a + b + c);

        Subscription listener = sum.subscribe(value -> {
        });
        assertEquals(1, first.subscribeCount);
        assertEquals(1, second.subscribeCount);
        assertEquals(1, third.subscribeCount);

        listener.close();
        assertEquals(1, first.closeCount);
        assertEquals(1, second.closeCount);
        assertEquals(1, third.closeCount);
    }

    @Test
    void ternaryCombineRejectsNullSourcesAndCombiner() {
        Signal<Integer> source = Signal.of(0);

        assertThrows(NullPointerException.class, () ->
                Value.combine(null, source, source, (a, b, c) -> a));
        assertThrows(NullPointerException.class, () ->
                Value.combine(source, null, source, (a, b, c) -> a));
        assertThrows(NullPointerException.class, () ->
                Value.combine(source, source, null, (a, b, c) -> a));
        assertThrows(NullPointerException.class, () ->
                Value.combine(source, source, source, null));
    }

    @Test
    void aSourceAtSeveralTernaryPositionsIsSubscribedOnceAndNeverMixesOldAndNewValues() {
        TrackingValue<Integer> repeated = new TrackingValue<>(1);
        TrackingValue<Integer> other = new TrackingValue<>(0);
        Value<String> combined = Value.combine(repeated, other, repeated,
                (first, second, third) -> first + ":" + second + ":" + third);
        List<String> values = new ArrayList<>();
        Subscription subscription = combined.subscribe(values::add);

        repeated.emit(2);

        assertEquals(List.of("2:0:2"), values);
        assertEquals(1, repeated.subscribeCount);
        assertEquals(1, other.subscribeCount);

        subscription.close();
        assertEquals(1, repeated.closeCount);
        assertEquals(1, other.closeCount);
    }

    @Test
    void ternaryOperandsSharingAnUpstreamPublishOneConsistentSnapshot() {
        Signal<Integer> source = Signal.of(1);
        Value<Integer> doubled = source.map(value -> value * 2);
        Value<Integer> negated = source.map(value -> -value);
        Value<String> combined = Value.combine(doubled, source, negated,
                (first, second, third) -> first + ":" + second + ":" + third);
        List<String> values = new ArrayList<>();
        combined.subscribe(values::add);

        source.set(3);

        assertEquals(List.of("6:3:-3"), values);
        assertEquals("6:3:-3", combined.get());
    }

    @Test
    void everyPublishedTernaryResultIsASimultaneousSnapshotOfItsSources() {
        Signal<Integer> first = Signal.of(0);
        Signal<Integer> second = Signal.of(0);
        Value<Integer> combined = Value.combine(first, second, first,
                (left, middle, right) -> left * 100 + middle * 10 + right);
        List<Integer> values = new ArrayList<>();
        combined.subscribe(values::add);

        first.set(1);
        second.set(2);
        first.set(3);

        for (Integer value : values) {
            assertEquals(value / 100, value % 10, "operands sharing a source disagreed in " + value);
        }
        assertEquals(List.of(101, 121, 323), values);
    }

    @Test
    void duplicateSourceUsesOneSubscriptionAndUpdatesBothOperands() {
        TrackingValue<Integer> source = new TrackingValue<>(1);
        Value<String> combined = Value.combine(source, source, (a, b) -> a + ":" + b);
        List<String> values = new ArrayList<>();
        Subscription subscription = combined.subscribe(values::add);

        source.emit(2);
        subscription.close();

        assertEquals(List.of("2:2"), values);
        assertEquals(1, source.subscribeCount);
        assertEquals(1, source.closeCount);
    }

    private static final class TrackingValue<T> implements Value<T> {

        private final List<Consumer<? super T>> listeners = new ArrayList<>();
        private T value;
        private RuntimeException subscribeFailure;
        private int subscribeCount;
        private int closeCount;

        private TrackingValue(T value) {
            this.value = value;
        }

        @Override
        public T get() {
            return this.value;
        }

        @Override
        public Subscription subscribe(Consumer<? super T> listener) {
            this.subscribeCount++;
            if (this.subscribeFailure != null) throw this.subscribeFailure;

            this.listeners.add(listener);
            return new Subscription() {

                private boolean active = true;

                @Override
                public void close() {
                    if (!this.active) return;

                    this.active = false;
                    TrackingValue.this.closeCount++;
                    TrackingValue.this.listeners.remove(listener);
                }
            };
        }

        private void emit(T value) {
            this.value = value;
            for (Consumer<? super T> listener : List.copyOf(this.listeners)) {
                listener.accept(value);
            }
        }
    }
}
