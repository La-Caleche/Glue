package fr.lacaleche.glue.mcsx.core.reactive;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The observer bookkeeping every {@link Source} needs: a subscription set plus the array snapshot
 * that notification iterates. Notifying from the live set would break on an observer that
 * subscribes or unsubscribes as it re-evaluates — which is what a {@link Computed} re-tracking its
 * dependencies does — so the array is cached and dirtied on membership change rather than rebuilt
 * on every notify.
 *
 * <p>Not a {@code Source} itself: the interface is sealed to {@link Signal} and {@link Computed},
 * and both inherit their {@code addObserver}/{@code removeObserver} from here.
 */
abstract class Observable {

    private final Set<Observer> observers = new LinkedHashSet<>();
    /** Cached notify order; null when a membership change dirtied it. */
    private Observer[] snapshot;

    public void addObserver(Observer observer) {
        if (observers.add(observer)) {
            snapshot = null;
        }
    }

    public void removeObserver(Observer observer) {
        if (observers.remove(observer)) {
            snapshot = null;
        }
    }

    final boolean hasObservers() {
        return !observers.isEmpty();
    }

    /** Invalidates every subscriber, over a snapshot that a re-entrant subscribe cannot disturb. */
    final void notifyObservers() {
        Observer[] current = snapshot;
        if (current == null) {
            current = observers.toArray(new Observer[0]);
            snapshot = current;
        }
        for (Observer observer : current) {
            observer.invalidate();
        }
    }

    final void clearObservers() {
        observers.clear();
        snapshot = null;
    }
}
