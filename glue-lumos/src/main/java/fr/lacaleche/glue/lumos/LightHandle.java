package fr.lacaleche.glue.lumos;

/**
 * A live reference to a light attached to something that moves, returned by
 * {@link Lumos#attach}. The light follows its target until the target dies or the handle is removed.
 */
public interface LightHandle {

    /** Replaces the light definition, keeping the attachment. Returns this handle for chaining. */
    LightHandle light(Light light);

    /** Detaches and removes the light. Idempotent. */
    void remove();

    boolean isRemoved();
}
