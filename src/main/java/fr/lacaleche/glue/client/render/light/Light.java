package fr.lacaleche.glue.client.render.light;

import org.joml.Vector3f;

/**
 * An immutable description of a single real-time light source.
 *
 * <p>Positions are stored in absolute world coordinates as {@code double}s; the
 * renderer converts them to camera-relative space before uploading to the GPU so
 * that reconstruction stays in single-precision-friendly ranges. Colors are
 * <em>linear</em> RGB in {@code [0, 1]}; {@link #intensity} scales them.</p>
 *
 * <p>Create instances with the static factories {@link #point} / {@link #spot} /
 * {@link #gobo}. Angles are supplied in degrees and pre-converted to cosines.</p>
 */
public final class Light {

    public final LightType type;

    /** Absolute world position. */
    public final double x;
    public final double y;
    public final double z;

    /** Normalized emission direction (SPOT / GOBO only; unused for POINT). */
    public final Vector3f direction;

    /** Linear RGB color, {@code [0, 1]} per channel. */
    public final float r;
    public final float g;
    public final float b;

    /** Multiplier applied to the color. */
    public final float intensity;

    /** Maximum reach in blocks; contribution falls to zero at this distance. */
    public final float range;

    /** Cosine of the inner cone half-angle (full brightness inside). */
    public final float cosInner;
    /** Cosine of the outer cone half-angle (zero brightness outside). */
    public final float cosOuter;

    /** GL texture id of the gobo mask, or {@code 0} for none. */
    public final int goboTextureId;

    /**
     * Whether this light renders a real shadow map: one for a spot or gobo, six
     * (a cube) for a point light.
     */
    public final boolean castsShadow;

    private Light(LightType type, double x, double y, double z, Vector3f direction,
                  float r, float g, float b, float intensity, float range,
                  float cosInner, float cosOuter, int goboTextureId) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = direction;
        this.r = r;
        this.g = g;
        this.b = b;
        this.intensity = intensity;
        this.range = range;
        this.cosInner = cosInner;
        this.cosOuter = cosOuter;
        this.goboTextureId = goboTextureId;
        this.castsShadow = true;
    }

    /**
     * An omnidirectional point light.
     */
    public static Light point(double x, double y, double z,
                              float r, float g, float b, float intensity, float range) {
        return new Light(LightType.POINT, x, y, z, new Vector3f(0f, -1f, 0f),
                r, g, b, intensity, range, 1f, -1f, 0);
    }

    /**
     * A cone light. {@code innerAngleDeg <= outerAngleDeg} are half-angles in degrees.
     */
    public static Light spot(double x, double y, double z,
                             float dirX, float dirY, float dirZ,
                             float r, float g, float b, float intensity, float range,
                             float innerAngleDeg, float outerAngleDeg) {
        return new Light(LightType.SPOT, x, y, z, normalized(dirX, dirY, dirZ),
                r, g, b, intensity, range,
                cos(innerAngleDeg), cos(outerAngleDeg), 0);
    }

    /**
     * A cone light modulated by a projected texture (gobo). The gobo's red channel
     * masks the cone; {@code goboTextureId} is a live GL texture id.
     */
    public static Light gobo(double x, double y, double z,
                             float dirX, float dirY, float dirZ,
                             float r, float g, float b, float intensity, float range,
                             float innerAngleDeg, float outerAngleDeg, int goboTextureId) {
        return new Light(LightType.GOBO, x, y, z, normalized(dirX, dirY, dirZ),
                r, g, b, intensity, range,
                cos(innerAngleDeg), cos(outerAngleDeg), goboTextureId);
    }

    private static Vector3f normalized(float x, float y, float z) {
        Vector3f v = new Vector3f(x, y, z);
        if (v.lengthSquared() < 1e-8f) {
            return new Vector3f(0f, -1f, 0f);
        }
        return v.normalize();
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }
}
