package fr.lacaleche.glue.mcsx.client.internal.text;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import net.minecraft.network.chat.Component;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class MinecraftText {

    private static final Signal<Long> RESOURCE_GENERATION = Signal.of(0L);
    private static final AtomicInteger ACTIVE_SUBSCRIPTIONS = new AtomicInteger();

    private MinecraftText() {
    }

    public static Value<String> value(Component component) {
        Component source = Objects.requireNonNull(component, "component");
        Value<String> localized = RESOURCE_GENERATION.map(generation -> source.getString());
        return new Value<>() {

            @Override
            public String get() {
                return localized.get();
            }

            @Override
            public Subscription subscribe(Consumer<? super String> listener) {
                Subscription subscription = localized.subscribe(listener);
                ACTIVE_SUBSCRIPTIONS.incrementAndGet();
                return new Subscription() {

                    private boolean active = true;

                    @Override
                    public void close() {
                        if (!this.active) return;

                        this.active = false;
                        try {
                            subscription.close();
                        } finally {
                            ACTIVE_SUBSCRIPTIONS.decrementAndGet();
                        }
                    }
                };
            }
        };
    }

    public static void resourcesReloaded() {
        if (!hasActiveSubscriptions()) return;

        RESOURCE_GENERATION.update(generation -> generation + 1);
    }

    private static boolean hasActiveSubscriptions() {
        return ACTIVE_SUBSCRIPTIONS.get() > 0;
    }
}
