package fr.lacaleche.glue.mcsx.client.dock.internal.view;

import fr.lacaleche.glue.mcsx.client.reactive.Subscription;
import fr.lacaleche.glue.mcsx.client.reactive.Value;
import fr.lacaleche.glue.mcsx.client.theme.Theme;
import icyllis.modernui.view.View;

import java.util.Objects;
import java.util.function.Consumer;

final class DockThemeBinding {

    private final View owner;
    private final Value<? extends Theme> source;
    private final Consumer<Theme> apply;
    private Subscription subscription;

    DockThemeBinding(View owner, Value<? extends Theme> source, Consumer<Theme> apply) {
        this.owner = owner;
        this.source = Objects.requireNonNull(source, "source");
        this.apply = Objects.requireNonNull(apply, "apply");
        this.apply.accept(Objects.requireNonNull(source.get(), "theme value"));
    }

    void attach() {
        if (this.subscription != null) return;

        Subscription acquired = this.source.subscribe(theme -> {
            this.apply.accept(Objects.requireNonNull(theme, "theme value"));
            this.owner.invalidate();
        });
        try {
            this.apply.accept(Objects.requireNonNull(this.source.get(), "theme value"));
            this.subscription = acquired;
        } catch (RuntimeException | Error failure) {
            try {
                acquired.close();
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }

    void applyCurrent() {
        this.apply.accept(Objects.requireNonNull(this.source.get(), "theme value"));
        this.owner.invalidate();
    }

    void detach() {
        if (this.subscription == null) return;

        this.subscription.close();
        this.subscription = null;
    }
}
