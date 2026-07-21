package fr.lacaleche.glue.consumer;

public interface QuadConsumer<K, V, S, U> {

    void accept(K k, V v, S s, U u);
}
