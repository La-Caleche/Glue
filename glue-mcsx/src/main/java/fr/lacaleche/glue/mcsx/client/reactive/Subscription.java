package fr.lacaleche.glue.mcsx.client.reactive;

/**
 * UI-thread-confined ownership of a listener or binding. MCSX subscriptions close idempotently.
 */
@FunctionalInterface
public interface Subscription extends AutoCloseable {

    @Override
    void close();
}
