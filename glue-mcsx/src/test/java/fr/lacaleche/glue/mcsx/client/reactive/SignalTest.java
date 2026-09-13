package fr.lacaleche.glue.mcsx.client.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SignalTest {

    @Test
    void equalValueDoesNotNotifyAndCloseIsIdempotent() {
        Signal<Integer> signal = Signal.of(1);
        List<Integer> values = new ArrayList<>();
        Subscription subscription = signal.subscribe(values::add);

        signal.set(1);
        signal.set(2);
        subscription.close();
        subscription.close();
        signal.set(3);

        assertEquals(List.of(2), values);
        assertEquals(3, signal.get());
    }

    @Test
    void postSetWithoutUiThreadAppliesSynchronouslyAndSuppressesEqualValues() {
        Signal<Integer> signal = Signal.of(1);
        List<Integer> values = new ArrayList<>();
        signal.subscribe(values::add);

        signal.postSet(2);
        signal.postSet(2);

        assertEquals(2, signal.get());
        assertEquals(List.of(2), values);
    }

    @Test
    void mappedValueSuppressesEqualMappedResults() {
        Signal<Integer> signal = Signal.of(0);
        Value<Integer> halves = signal.map(value -> value / 2);
        List<Integer> values = new ArrayList<>();
        halves.subscribe(values::add);

        signal.set(1);
        signal.set(2);
        signal.set(3);
        signal.set(4);

        assertEquals(List.of(1, 2), values);
        assertEquals(2, halves.get());
    }

    @Test
    void nestedWritesReachEveryListenerInFifoOrder() {
        Signal<Integer> signal = Signal.of(0);
        List<String> events = new ArrayList<>();
        signal.subscribe(value -> {
            events.add("first:" + value);
            if (value == 1) {
                signal.set(2);
            }
        });
        signal.subscribe(value -> events.add("second:" + value));

        signal.set(1);

        assertEquals(List.of(
                "first:1",
                "second:1",
                "first:2",
                "second:2"
        ), events);
    }

    @Test
    void closingListenerDuringDispatchSkipsItsPendingCallback() {
        Signal<Integer> signal = Signal.of(0);
        List<String> events = new ArrayList<>();
        SubscriptionHolder second = new SubscriptionHolder();
        signal.subscribe(value -> {
            events.add("first");
            second.subscription.close();
        });
        second.subscription = signal.subscribe(value -> events.add("second"));

        signal.set(1);

        assertEquals(List.of("first"), events);
    }

    @Test
    void failingListenerDoesNotPreventOtherNotifications() {
        Signal<Integer> signal = Signal.of(0);
        List<Integer> values = new ArrayList<>();
        signal.subscribe(value -> {
            throw new IllegalStateException("broken listener");
        });
        signal.subscribe(values::add);

        assertThrows(IllegalStateException.class, () -> signal.set(1));

        assertEquals(List.of(1), values);
        assertEquals(1, signal.get());
    }

    private static final class SubscriptionHolder {

        private Subscription subscription;
    }
}
