package fr.lacaleche.glue.mcsx.core.reactive;

/** Something that re-evaluates when a {@link Source} it read changes: a {@link Computed} or an {@link Effect}. */
sealed interface Observer permits Computed, Effect {

    /** A dependency changed; the observer must re-evaluate (eagerly or lazily). */
    void invalidate();

    /** Recorded during evaluation: "I read this source." Used to unsubscribe on the next run. */
    void onDependency(Source source);
}
