package fr.lacaleche.glue.consumer;

public interface QuadConsumer<K, V, S, U> {

    /**
     * Performs the operation given the specified arguments.\
     *
     * @param k the first input argument
     * @param v the second input argument
     * @param s the third input argument
     * @param u the fourth input argument
     */
    void accept(K k, V v, S s, U u);
}
