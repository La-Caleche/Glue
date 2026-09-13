package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A value derived from any number of sources at once.
 *
 * <p>Every published result is recomputed from all sources together rather than from the operand a
 * notification happened to carry, so a listener only ever sees a snapshot in which every source is
 * current. That is what makes a source safe to use at several operand positions, and what keeps two
 * operands that share an upstream from ever being read one change apart.</p>
 */
final class CombinedValue<R> implements Value<R> {

    private final List<Value<?>> sources;
    private final Supplier<? extends R> currentValue;
    private final List<Listener> listeners = new ArrayList<>();
    private final ArrayDeque<Notification<R>> pendingNotifications = new ArrayDeque<>();
    private Generation generation;
    private boolean dispatching;

    CombinedValue(List<? extends Value<?>> sources, Supplier<? extends R> currentValue) {
        this.sources = distinctSources(sources);
        this.currentValue = currentValue;
    }

    @Override
    public R get() {
        return this.currentValue.get();
    }

    @Override
    public Subscription subscribe(Consumer<? super R> listener) {
        UiThreadGuard.check();
        Consumer<? super R> required = Objects.requireNonNull(listener, "listener");
        if (this.generation == null) {
            this.activate();
        }

        Listener entry = new Listener(required);
        this.listeners.add(entry);
        return entry;
    }

    private void activate() {
        Generation candidate = new Generation();
        candidate.lastValue = this.get();
        candidate.active = true;
        this.generation = candidate;
        try {
            for (Value<?> source : this.sources) {
                candidate.subscriptions.add(Objects.requireNonNull(
                        source.subscribe(ignored -> candidate.sourceChanged()),
                        "source subscription"
                ));
            }
        } catch (RuntimeException exception) {
            this.generation = null;
            candidate.active = false;
            try {
                candidate.closeSubscriptions();
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    private void deactivate() {
        Generation active = this.generation;
        if (active == null) return;

        this.generation = null;
        active.active = false;
        this.pendingNotifications.clear();
        active.closeSubscriptions();
    }

    private void publish(R value) {
        if (this.generation == null || Objects.equals(this.generation.lastValue, value)) return;

        this.generation.lastValue = value;
        this.pendingNotifications.addLast(new Notification<>(value));
        this.drainNotifications();
    }

    private void drainNotifications() {
        if (this.dispatching) return;

        this.dispatching = true;
        RuntimeException failure = null;
        try {
            while (!this.pendingNotifications.isEmpty()) {
                R value = this.pendingNotifications.removeFirst().value;
                List<Listener> snapshot = List.copyOf(this.listeners);
                for (Listener listener : snapshot) {
                    if (!listener.active) continue;

                    try {
                        listener.consumer.accept(value);
                    } catch (RuntimeException exception) {
                        failure = combine(failure, exception);
                    }
                }
            }
        } finally {
            this.dispatching = false;
        }
        if (failure != null) throw failure;
    }

    private static RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) return next;

        first.addSuppressed(next);
        return first;
    }

    /**
     * One entry per distinct source object. A source holding several operand positions must be
     * subscribed once: subscribing it per position would publish one result per position for a
     * single change, and every result but the last would carry that source at two different values.
     */
    private static List<Value<?>> distinctSources(List<? extends Value<?>> sources) {
        Set<Value<?>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        List<Value<?>> distinct = new ArrayList<>(sources.size());
        for (Value<?> source : sources) {
            if (seen.add(source)) distinct.add(source);
        }
        return List.copyOf(distinct);
    }

    private final class Generation {

        private final List<Subscription> subscriptions = new ArrayList<>();
        private R lastValue;
        private boolean active;

        private void sourceChanged() {
            if (!this.active || CombinedValue.this.generation != this) return;

            CombinedValue.this.publish(CombinedValue.this.get());
        }

        private void closeSubscriptions() {
            List<Subscription> acquired = List.copyOf(this.subscriptions);
            this.subscriptions.clear();
            RuntimeException failure = null;
            for (int index = acquired.size() - 1; index >= 0; index--) {
                try {
                    acquired.get(index).close();
                } catch (RuntimeException exception) {
                    failure = combine(failure, exception);
                }
            }
            if (failure != null) throw failure;
        }
    }

    private final class Listener implements Subscription {

        private final Consumer<? super R> consumer;
        private boolean active = true;

        private Listener(Consumer<? super R> consumer) {
            this.consumer = consumer;
        }

        @Override
        public void close() {
            UiThreadGuard.check();
            if (!this.active) return;

            this.active = false;
            CombinedValue.this.listeners.remove(this);
            if (CombinedValue.this.listeners.isEmpty()) {
                CombinedValue.this.deactivate();
            }
        }
    }

    private record Notification<T>(T value) {
    }
}
