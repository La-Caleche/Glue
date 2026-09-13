package fr.lacaleche.glue.mcsx.client.component;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Token;
import icyllis.modernui.view.View;

import java.util.Objects;
import java.util.function.Consumer;

final class ThemeValue<T> implements Value<T> {

    private final View view;
    private final Token<T> token;

    ThemeValue(View view, Token<T> token) {
        this.view = Objects.requireNonNull(view, "view");
        this.token = Objects.requireNonNull(token, "token");
    }

    @Override
    public T get() {
        ThemeScope scope = TaffyLayout.findThemeScope(this.view);
        return scope == null ? this.token.fallback() : scope.get(this.token);
    }

    @Override
    public Subscription subscribe(Consumer<? super T> listener) {
        Objects.requireNonNull(listener, "listener");
        ThemeScope scope = TaffyLayout.findThemeScope(this.view);
        if (scope == null) return () -> {
        };

        return scope.value(this.token).subscribe(listener);
    }
}
