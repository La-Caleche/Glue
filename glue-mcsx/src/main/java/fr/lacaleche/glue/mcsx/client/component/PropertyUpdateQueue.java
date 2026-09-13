package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.internal.property.Property;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

final class PropertyUpdateQueue {

    private final Consumer<Runnable> uiPoster;
    private final Consumer<RuntimeException> failureReporter;
    private final Map<View, Map<Property<?, ?>, PendingUpdate>> pending = new IdentityHashMap<>();
    private boolean scheduled;
    private int successfulApplicationCount;

    PropertyUpdateQueue(
            Consumer<Runnable> uiPoster,
            Consumer<RuntimeException> failureReporter
    ) {
        this.uiPoster = uiPoster;
        this.failureReporter = failureReporter;
    }

    void enqueue(View view, Property<?, ?> property, BooleanSupplier application) {
        this.pending.computeIfAbsent(view, key -> new IdentityHashMap<>())
                .put(property, new PendingUpdate(view, property, application));
        if (this.scheduled) return;

        this.scheduled = true;
        try {
            this.uiPoster.accept(this::flush);
        } catch (RuntimeException exception) {
            this.scheduled = false;
            this.pending.clear();
            throw exception;
        }
    }

    void clear() {
        this.pending.clear();
    }

    int successfulApplicationCount() {
        return this.successfulApplicationCount;
    }

    private void flush() {
        List<PendingUpdate> applications = new ArrayList<>();
        for (Map<Property<?, ?>, PendingUpdate> viewUpdates : this.pending.values()) {
            applications.addAll(viewUpdates.values());
        }
        this.pending.clear();
        this.scheduled = false;

        RuntimeException failure = null;
        for (PendingUpdate update : applications) {
            try {
                if (update.application.getAsBoolean()) {
                    this.successfulApplicationCount++;
                }
            } catch (RuntimeException exception) {
                RuntimeException contextual = new IllegalStateException(
                        "Failed to apply property '" + update.property.name()
                                + "' to " + update.view.getClass().getSimpleName(),
                        exception
                );
                if (failure == null) {
                    failure = contextual;
                } else {
                    failure.addSuppressed(contextual);
                }
            }
        }
        if (failure != null) {
            this.failureReporter.accept(failure);
        }
    }

    private record PendingUpdate(
            View view,
            Property<?, ?> property,
            BooleanSupplier application
    ) {
    }
}
