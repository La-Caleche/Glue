package fr.lacaleche.glue.mcsx.core.reactive;

/** Something that can be subscribed to: a {@link Signal} or a {@link Computed}. */
sealed interface Source permits Signal, Computed {

    void addObserver(Observer observer);

    void removeObserver(Observer observer);
}
