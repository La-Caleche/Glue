package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.View;

import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class PropertyScope<V extends View> {

    private final V view;
    private final Map<Property<V, ?>, Slot<?>> slots = new IdentityHashMap<>();
    private boolean mounted;

    PropertyScope(V view) {
        this.view = view;
    }

    <T> void set(Property<V, T> property, T value) {
        this.set(property, PropertyOrigin.LOCAL, value);
    }

    <T> void bind(Property<V, T> property, Value<? extends T> value) {
        this.bind(property, PropertyOrigin.LOCAL, value);
    }

    <T> void setComponent(Property<V, T> property, T value) {
        this.set(property, PropertyOrigin.COMPONENT, value);
    }

    <T> void setStylesheet(Property<V, T> property, T value) {
        this.set(property, PropertyOrigin.STYLESHEET, value);
    }

    <T> void bindStylesheet(Property<V, T> property, Value<? extends T> value) {
        this.bind(property, PropertyOrigin.STYLESHEET, value);
    }

    <T> void clearStylesheet(Property<V, T> property) {
        this.checkUiThread();
        Slot<T> slot = this.findSlot(property);
        if (slot != null) {
            slot.remove(PropertyOrigin.STYLESHEET);
        }
    }

    void mount() {
        this.checkUiThread();
        if (this.mounted) return;

        this.mounted = true;
        try {
            for (Slot<?> slot : this.slots.values()) {
                slot.mount();
            }
        } catch (RuntimeException exception) {
            this.mounted = false;
            for (Slot<?> slot : this.slots.values()) {
                try {
                    slot.unmount();
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    void unmount() {
        this.checkUiThread();
        if (!this.mounted) return;

        this.mounted = false;
        RuntimeException failure = null;
        for (Slot<?> slot : this.slots.values()) {
            try {
                slot.unmount();
            } catch (RuntimeException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) throw failure;
    }

    private <T> void set(Property<V, T> property, PropertyOrigin origin, T value) {
        this.checkUiThread();
        this.slot(property).replace(origin, Source.constant(value));
    }

    private <T> void bind(
            Property<V, T> property,
            PropertyOrigin origin,
            Value<? extends T> value
    ) {
        this.checkUiThread();
        this.slot(property).replace(origin, Source.dynamic(value));
    }

    @SuppressWarnings("unchecked")
    private <T> Slot<T> findSlot(Property<V, T> property) {
        return (Slot<T>) this.slots.get(property);
    }

    private <T> Slot<T> slot(Property<V, T> property) {
        Slot<T> slot = this.findSlot(property);
        if (slot != null) return slot;

        Slot<T> created = new Slot<>(Objects.requireNonNull(property, "property"));
        this.slots.put(property, created);
        return created;
    }

    private void checkUiThread() {
        if (Core.getUiThread() != null) {
            Core.checkUiThread();
        }
    }

    private final class Slot<T> {

        private final Property<V, T> property;
        private final EnumMap<PropertyOrigin, Source<T>> sources = new EnumMap<>(
                PropertyOrigin.class
        );
        private T lastApplied;
        private boolean hasApplied;
        private boolean updatePending;
        private long generation;

        private Slot(Property<V, T> property) {
            this.property = property;
        }

        private void replace(PropertyOrigin origin, Source<T> source) {
            Source<T> previous = this.sources.put(origin, source);
            if (previous != null) {
                previous.unmount();
            }
            this.generation++;
            if (PropertyScope.this.mounted) {
                source.mount(value -> this.sourceChanged(source, value));
                this.enqueueWinner();
            } else {
                this.applyWinnerImmediately();
            }
        }

        private void remove(PropertyOrigin origin) {
            Source<T> removed = this.sources.remove(origin);
            if (removed == null) return;

            removed.unmount();
            this.generation++;
            if (PropertyScope.this.mounted) {
                this.enqueueWinner();
            } else {
                this.applyWinnerImmediately();
            }
        }

        private void mount() {
            this.generation++;
            try {
                for (Source<T> source : this.sources.values()) {
                    source.mount(value -> this.sourceChanged(source, value));
                }
                this.enqueueWinner();
            } catch (RuntimeException exception) {
                this.updatePending = false;
                for (Source<T> source : this.sources.values()) {
                    try {
                        source.unmount();
                    } catch (RuntimeException cleanupFailure) {
                        exception.addSuppressed(cleanupFailure);
                    }
                }
                throw exception;
            }
        }

        private void unmount() {
            this.generation++;
            this.updatePending = false;
            RuntimeException failure = null;
            for (Source<T> source : this.sources.values()) {
                try {
                    source.unmount();
                } catch (RuntimeException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
            }
            if (failure != null) throw failure;
        }

        private void sourceChanged(Source<T> source, T value) {
            if (Objects.equals(source.current, value)) return;

            source.current = value;
            if (source != this.winner()) return;

            this.generation++;
            this.enqueueWinner();
        }

        private void enqueueWinner() {
            Source<T> winner = this.winner();
            if (winner == null) return;
            if (!this.updatePending
                    && this.hasApplied
                    && Objects.equals(this.lastApplied, winner.current)) return;

            PropertyUpdateQueue queue = TaffyLayout.findPropertyUpdateQueue(
                    PropertyScope.this.view
            );
            if (queue == null) {
                this.updatePending = false;
                this.applyImmediately(winner.current);
                return;
            }

            long queuedGeneration = this.generation;
            T queuedValue = winner.current;
            this.updatePending = true;
            queue.enqueue(PropertyScope.this.view, this.property, () -> {
                if (!PropertyScope.this.mounted || queuedGeneration != this.generation) return false;
                if (TaffyLayout.findPropertyUpdateQueue(PropertyScope.this.view) != queue) return false;

                this.updatePending = false;
                return this.applyImmediately(queuedValue);
            });
        }

        private void applyWinnerImmediately() {
            Source<T> winner = this.winner();
            if (winner != null) {
                this.applyImmediately(winner.current);
            }
        }

        private boolean applyImmediately(T nextValue) {
            if (this.hasApplied && Objects.equals(this.lastApplied, nextValue)) return false;

            this.property.apply(PropertyScope.this.view, nextValue);
            this.lastApplied = nextValue;
            this.hasApplied = true;
            return true;
        }

        private Source<T> winner() {
            PropertyOrigin[] origins = PropertyOrigin.values();
            for (int index = origins.length - 1; index >= 0; index--) {
                Source<T> source = this.sources.get(origins[index]);
                if (source != null) return source;
            }
            return null;
        }
    }

    private static final class Source<T> {

        private final Value<? extends T> value;
        private T current;
        private Subscription subscription;

        private Source(Value<? extends T> value, T current) {
            this.value = value;
            this.current = current;
        }

        private static <T> Source<T> constant(T value) {
            return new Source<>(null, value);
        }

        private static <T> Source<T> dynamic(Value<? extends T> value) {
            Value<? extends T> required = Objects.requireNonNull(value, "value");
            return new Source<>(required, required.get());
        }

        private void mount(Consumer<T> listener) {
            if (this.value == null || this.subscription != null) return;

            Subscription acquired = this.value.subscribe(listener);
            try {
                this.current = this.value.get();
                this.subscription = acquired;
            } catch (RuntimeException exception) {
                try {
                    acquired.close();
                } catch (RuntimeException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        }

        private void unmount() {
            Subscription acquired = this.subscription;
            if (acquired == null) return;

            this.subscription = null;
            acquired.close();
        }
    }
}
