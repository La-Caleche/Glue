package fr.lacaleche.glue.client.render.internal.gbuffer;

/**
 * What a G-buffer pixel <em>is</em>. Written into the material-id attachment by the geometry
 * pass and read back by Lumos consumers, so a pane, a mob and the floor are told apart by
 * identity rather than by a fragile depth comparison.
 *
 * <p>The {@link #id} is the value stored in the red channel of the id attachment, encoded as
 * {@code id / 255.0} so it survives an {@code RGBA8} target and decodes with a rounded
 * multiply. {@link #NONE} (0) is the cleared value: no material was written (sky, or a surface
 * the capture does not cover such as translucent glass). New surface types get a new constant
 * here and a shader that writes it -- no new depth heuristics.
 */
public enum MaterialClass {

    NONE(0),
    TERRAIN(1),
    ENTITY(2),
    PARTICLE(3),
    GLASS(4),
    WATER(5),
    METAL(6);

    private final int id;

    MaterialClass(int id) {
        this.id = id;
    }

    /** Raw id stored in the buffer (0..255). */
    public int id() {
        return id;
    }

    /** Normalised id as written by a shader into an {@code RGBA8} red channel. */
    public float encoded() {
        return id / 255.0f;
    }
}
