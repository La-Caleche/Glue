package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Reconciles one container's children against a keyed collection. Every key owns a {@link Signal}
 * carrying its current item, so a row observes its own value instead of re-deriving itself from the
 * whole list: the reconciliation already visits every item, which makes the update O(1) per key.
 */
final class KeyedChildren<T, K> {

    private final TaffyLayout owner;
    private final Value<? extends List<? extends T>> items;
    private final Function<? super T, ? extends K> keyExtractor;
    private final Function<? super Value<T>, ? extends View> viewFactory;
    private final Map<K, View> viewsByKey = new LinkedHashMap<>();
    private final Map<K, Signal<T>> valuesByKey = new LinkedHashMap<>();
    private Subscription subscription;

    KeyedChildren(
            TaffyLayout owner,
            Value<? extends List<? extends T>> items,
            Function<? super T, ? extends K> keyExtractor,
            Function<? super Value<T>, ? extends View> viewFactory
    ) {
        this.owner = owner;
        this.items = Objects.requireNonNull(items, "items");
        this.keyExtractor = Objects.requireNonNull(keyExtractor, "keyExtractor");
        this.viewFactory = Objects.requireNonNull(viewFactory, "viewFactory");
        this.reconcile(this.requireItems());
    }

    void mount() {
        this.checkUiThread();
        if (this.subscription != null) return;

        Subscription acquired = this.items.subscribe(this::reconcile);
        try {
            this.reconcile(this.requireItems());
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

    void unmount() {
        this.checkUiThread();
        Subscription acquired = this.subscription;
        if (acquired == null) return;

        this.subscription = null;
        acquired.close();
    }

    private void reconcile(List<? extends T> nextItems) {
        this.checkUiThread();
        Objects.requireNonNull(nextItems, "items value");

        List<K> nextKeys = new ArrayList<>(nextItems.size());
        Set<K> uniqueKeys = new HashSet<>();
        for (int index = 0; index < nextItems.size(); index++) {
            T item = Objects.requireNonNull(nextItems.get(index), "item at index " + index);
            K key = Objects.requireNonNull(
                    this.keyExtractor.apply(item),
                    "key at index " + index
            );
            if (!uniqueKeys.add(key)) {
                throw new IllegalArgumentException(
                        "Duplicate dynamic child key '" + key + "' at index " + index
                );
            }
            nextKeys.add(key);
        }

        Map<K, View> nextViewsByKey = new LinkedHashMap<>();
        Map<K, Signal<T>> nextValuesByKey = new LinkedHashMap<>();
        List<View> nextViews = new ArrayList<>(nextItems.size());
        Set<View> uniqueViews = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int index = 0; index < nextItems.size(); index++) {
            T item = nextItems.get(index);
            K key = nextKeys.get(index);
            View view = this.viewsByKey.get(key);
            Signal<T> value = this.valuesByKey.get(key);
            if (view == null) {
                value = Signal.of(item);
                view = Objects.requireNonNull(
                        this.viewFactory.apply(value),
                        "view for key '" + key + "'"
                );
                if (view.getParent() != null) {
                    throw new IllegalArgumentException(
                            "New View for dynamic child key '" + key + "' already has a parent"
                    );
                }
            }
            if (!uniqueViews.add(view)) {
                throw new IllegalArgumentException(
                        "View factory reused one View for multiple dynamic child keys"
                );
            }

            nextViewsByKey.put(key, view);
            nextValuesByKey.put(key, value);
            nextViews.add(view);
        }

        this.owner.reconcileKeyedChildren(() -> {
            for (Map.Entry<K, View> entry : this.viewsByKey.entrySet()) {
                if (!nextViewsByKey.containsKey(entry.getKey())) {
                    this.owner.removeView(entry.getValue());
                }
            }
            for (View view : nextViews) {
                if (view.getParent() == null) {
                    this.owner.addView(view);
                }
            }
            this.owner.synchronizeChildOrder(nextViews);
        });

        this.viewsByKey.clear();
        this.viewsByKey.putAll(nextViewsByKey);
        this.valuesByKey.clear();
        this.valuesByKey.putAll(nextValuesByKey);
        this.publish(nextItems, nextKeys);
    }

    /**
     * Pushes each item into the value its key owns once the child list is consistent, so a listener
     * never observes a half-reconciled container. A key whose item is unchanged dispatches nothing,
     * and a key created by this pass already carries its item.
     */
    private void publish(List<? extends T> nextItems, List<K> nextKeys) {
        RuntimeException failure = null;
        for (int index = 0; index < nextItems.size(); index++) {
            Signal<T> value = this.valuesByKey.get(nextKeys.get(index));
            T item = nextItems.get(index);
            failure = LifecycleCleanup.attempt(failure, () -> value.set(item));
        }
        LifecycleCleanup.finish(failure);
    }

    private List<? extends T> requireItems() {
        return Objects.requireNonNull(this.items.get(), "items value");
    }

    private void checkUiThread() {
        if (Core.getUiThread() != null) {
            Core.checkUiThread();
        }
    }
}
