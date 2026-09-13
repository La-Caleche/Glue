package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import fr.lacaleche.glue.mcsx.client.theme.Token;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

final class ThemeScope {

    private final Value<? extends Theme> source;
    private final Map<Token<?>, TokenValue<?>> dependencies = new IdentityHashMap<>();
    private Theme current;
    private Subscription subscription;

    ThemeScope(Value<? extends Theme> source) {
        this.source = Objects.requireNonNull(source, "source");
        this.current = Objects.requireNonNull(source.get(), "theme value");
    }

    void mount() {
        if (this.subscription != null) return;

        Subscription acquired = this.source.subscribe(this::apply);
        try {
            this.apply(Objects.requireNonNull(this.source.get(), "theme value"));
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
        Subscription acquired = this.subscription;
        if (acquired == null) return;

        this.subscription = null;
        acquired.close();
    }

    <T> T get(Token<T> token) {
        return this.current.get(token);
    }

    <T> Value<T> value(Token<T> token) {
        @SuppressWarnings("unchecked")
        TokenValue<T> existing = (TokenValue<T>) this.dependencies.get(token);
        if (existing != null) return existing;

        TokenValue<T> created = new TokenValue<>(token, this.current);
        this.dependencies.put(token, created);
        return created;
    }

    int dependencyCount() {
        return this.dependencies.size();
    }

    private void apply(Theme theme) {
        Theme next = Objects.requireNonNull(theme, "theme");
        this.current = next;
        RuntimeException failure = null;
        for (TokenValue<?> dependency : List.copyOf(this.dependencies.values())) {
            failure = LifecycleCleanup.attempt(failure, () -> dependency.apply(next));
        }
        LifecycleCleanup.finish(failure);
    }

    private final class TokenValue<T> implements Value<T> {

        private final Token<T> token;
        private final Signal<T> value;
        private int subscriberCount;

        private TokenValue(Token<T> token, Theme theme) {
            this.token = token;
            this.value = Signal.of(theme.get(token));
        }

        private void apply(Theme theme) {
            this.value.set(theme.get(this.token));
        }

        @Override
        public T get() {
            return this.value.get();
        }

        @Override
        public Subscription subscribe(Consumer<? super T> listener) {
            Subscription acquired = this.value.subscribe(listener);
            this.subscriberCount++;
            return new Subscription() {

                private boolean closed;

                @Override
                public void close() {
                    if (this.closed) return;

                    this.closed = true;
                    try {
                        acquired.close();
                    } finally {
                        TokenValue.this.subscriberCount--;
                        if (TokenValue.this.subscriberCount == 0) {
                            ThemeScope.this.dependencies.remove(
                                    TokenValue.this.token,
                                    TokenValue.this
                            );
                        }
                    }
                }
            };
        }
    }
}
