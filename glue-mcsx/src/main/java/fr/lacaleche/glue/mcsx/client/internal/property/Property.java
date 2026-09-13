package fr.lacaleche.glue.mcsx.client.internal.property;

import icyllis.modernui.view.View;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * A typed View property whose applier is responsible for any paint or layout invalidation the
 * change requires.
 */
public final class Property<V extends View, T> {

    private final String name;
    private final BiConsumer<? super V, ? super T> applier;

    private Property(String name, BiConsumer<? super V, ? super T> applier) {
        if (name.isBlank()) throw new IllegalArgumentException("Property name cannot be blank");

        this.name = name;
        this.applier = applier;
    }

    public static <V extends View, T> Property<V, T> create(
            String name,
            BiConsumer<? super V, ? super T> applier
    ) {
        return new Property<>(
                Objects.requireNonNull(name, "name"),
                Objects.requireNonNull(applier, "applier")
        );
    }

    public String name() {
        return this.name;
    }

    public void apply(V view, T value) {
        this.applier.accept(view, value);
    }
}
