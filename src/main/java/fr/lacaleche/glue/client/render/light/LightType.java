package fr.lacaleche.glue.client.render.light;

/**
 * The emission shape of a {@link Light}.
 *
 * <p>The ordinal is passed directly to the deferred lighting shader as the
 * {@code LightType} uniform, so the declaration order is part of the shader
 * contract (see {@code assets/glue/shaders/internal/glue_light_deferred.fsh}).</p>
 */
public enum LightType {
    /** Omnidirectional point light with distance falloff. */
    POINT,
    /** Cone light with an inner/outer angle falloff around a direction. */
    SPOT,
    /** A spot light modulated by a projected texture (gobo). */
    GOBO
}
