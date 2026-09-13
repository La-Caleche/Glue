package fr.lacaleche.glue.mcsx.client.reactive;

import icyllis.modernui.annotation.UiThread;
import icyllis.modernui.core.Core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A mutable value with equality suppression and synchronous FIFO listener dispatch.
 */
@UiThread
public final class Signal<T> implements Value<T> {

    private final List<Listener<T>> listeners = new ArrayList<>();
    private final ArrayDeque<Notification<T>> pendingNotifications = new ArrayDeque<>();
    private T value;
    private boolean dispatching;

    public Signal(T initialValue) {
        this.value = initialValue;
    }

    public static <T> Signal<T> of(T initialValue) {
        return new Signal<>(initialValue);
    }

    @Override
    public T get() {
        return this.value;
    }

    public void set(T value) {
        UiThreadGuard.check();
        if (Objects.equals(this.value, value)) return;

        this.value = value;
        this.pendingNotifications.addLast(new Notification<>(value));
        this.drainNotifications();
    }

    public void update(UnaryOperator<T> updater) {
        UiThreadGuard.check();
        this.set(Objects.requireNonNull(updater, "updater").apply(this.value));
    }

    /**
     * Sets this signal from any thread. On the UI thread — or when no UI thread exists, as in unit
     * tests — the value is applied immediately like {@link #set}; from any other thread it is posted
     * to the UI thread, in FIFO order with other posted work.
     */
    public void postSet(T value) {
        if (Core.getUiThread() == null || Core.isOnUiThread()) {
            this.set(value);
            return;
        }
        Core.postOnUiThread(() -> this.set(value));
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        UiThreadGuard.check();
        Listener<T> entry = new Listener<>(
                Objects.requireNonNull(listener, "listener"),
                this.listeners
        );
        this.listeners.add(entry);
        return entry;
    }

    private void drainNotifications() {
        if (this.dispatching) return;

        this.dispatching = true;
        RuntimeException failure = null;
        try {
            while (!this.pendingNotifications.isEmpty()) {
                T notifiedValue = this.pendingNotifications.removeFirst().value;
                List<Listener<T>> snapshot = List.copyOf(this.listeners);
                for (Listener<T> listener : snapshot) {
                    if (!listener.active) continue;

                    try {
                        listener.consumer.accept(notifiedValue);
                    } catch (RuntimeException exception) {
                        failure = this.combine(failure, exception);
                    }
                }
            }
        } finally {
            this.dispatching = false;
        }

        if (failure != null) throw failure;
    }

    private RuntimeException combine(RuntimeException first, RuntimeException next) {
        if (first == null) return next;

        first.addSuppressed(next);
        return first;
    }

    private static final class Listener<T> implements Subscription {

        private final Consumer<? super T> consumer;
        private final List<Listener<T>> owner;
        private boolean active = true;

        private Listener(Consumer<? super T> consumer, List<Listener<T>> owner) {
            this.consumer = consumer;
            this.owner = owner;
        }

        @Override
        public void close() {
            UiThreadGuard.check();
            if (!this.active) return;

            this.active = false;
            this.owner.remove(this);
        }
    }

    private record Notification<T>(T value) {
    }
}
