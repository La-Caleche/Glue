package fr.lacaleche.glue.mcsx.client.reactive;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * A value owned by Minecraft's client thread and mirrored into a UI-thread {@link Value}. It replaces
 * the hand-rolled "plain field plus Signal twin" bridge: the owner reads and writes {@link #get()} /
 * {@link #set} on the client thread, the UI binds {@link #value()}, and each changed value crosses to
 * the UI thread exactly once.
 *
 * <p>{@link #get()} and {@link #set} reject every other thread — ModernUI's UI thread, the server
 * thread, network and worker threads — so the owning copy is confined rather than merely conventional.
 * Without a running client, as in unit tests, there is no client thread to compare against and only
 * the UI thread is ruled out.</p>
 *
 * <p>{@link #set} suppresses equal values before crossing, so unchanged state never queues UI work.
 * Published values are shared between both threads by reference — publish immutable snapshots.
 * Without an initialized UI thread, as in unit tests, everything applies synchronously.</p>
 */
public final class ClientMirror<T> {

    private final Signal<T> mirror;
    private final BooleanSupplier onOwningThread;
    private volatile T value;

    private ClientMirror(T initialValue, BooleanSupplier onOwningThread) {
        this.onOwningThread = onOwningThread;
        this.value = initialValue;
        this.mirror = Signal.of(initialValue);
    }

    public static <T> ClientMirror<T> of(T initialValue) {
        return new ClientMirror<>(initialValue, ClientThreadGuard::isOnClientThread);
    }

    static <T> ClientMirror<T> of(T initialValue, BooleanSupplier onOwningThread) {
        return new ClientMirror<>(initialValue, Objects.requireNonNull(onOwningThread, "onOwningThread"));
    }

    /** The owning thread's current copy, unaffected by UI-thread scheduling. */
    public T get() {
        this.checkOwningThread();
        return this.value;
    }

    /** Updates the owning thread's copy and publishes the change to {@link #value()}. */
    public void set(T value) {
        this.checkOwningThread();
        if (Objects.equals(this.value, value)) return;

        this.value = value;
        this.mirror.postSet(value);
    }

    /** The UI-thread view of this value, for bindings and subscriptions. */
    public Value<T> value() {
        return this.mirror;
    }

    private void checkOwningThread() {
        if (this.onOwningThread.getAsBoolean()) return;

        throw new IllegalStateException("A ClientMirror is confined to Minecraft's client thread");
    }
}
