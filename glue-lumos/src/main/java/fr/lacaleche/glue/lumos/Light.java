package fr.lacaleche.glue.lumos;

/**
 * An immutable description of a single real-time light source.
 *
 * <p>Positions are stored in absolute world coordinates as {@code double}s; the
 * renderer converts them to camera-relative space before uploading to the GPU so
 * that reconstruction stays in single-precision-friendly ranges. Colors are
 * <em>linear</em> RGB in {@code [0, 1]}; {@link #intensity} scales them.</p>
 *
 * <p>Create instances with {@link #point(double, double, double, float, float, float, float, float)},
 * {@link #spot(double, double, double, float, float, float, float, float, float, float, float, float, float)},
 * or {@link #gobo(double, double, double, float, float, float, float, float, float, float, float, float, float, int)}.
 * Angles are supplied in degrees and pre-converted to cosines.</p>
 */
public final class Light {

    public final LightType type;

    /** Absolute world position. */
    public final double x;
    public final double y;
    public final double z;

    /** Normalized emission direction (SPOT / GOBO only; unused for POINT). */
    public final float directionX;
    public final float directionY;
    public final float directionZ;

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

    private Light(LightType type, double x, double y, double z,
                  float directionX, float directionY, float directionZ,
                  float r, float g, float b, float intensity, float range,
                  float cosInner, float cosOuter, int goboTextureId, boolean castsShadow) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.z = z;
        this.directionX = directionX;
        this.directionY = directionY;
        this.directionZ = directionZ;
        this.r = r;
        this.g = g;
        this.b = b;
        this.intensity = intensity;
        this.range = range;
        this.cosInner = cosInner;
        this.cosOuter = cosOuter;
        this.goboTextureId = goboTextureId;
        this.castsShadow = castsShadow;
    }

    /**
     * Reconstructs a light directly from its stored fields, bypassing the angle-to-cosine conversion the
     * public factories apply. For serialization only ({@link LightCodecs}) &mdash; it trusts its inputs,
     * so it takes the cone cosines as stored rather than re-deriving them from degrees.
     */
    static Light raw(LightType type, double x, double y, double z,
                     float directionX, float directionY, float directionZ,
                     float r, float g, float b, float intensity, float range,
                     float cosInner, float cosOuter, boolean castsShadow) {
        return new Light(type, x, y, z, directionX, directionY, directionZ,
                r, g, b, intensity, range, cosInner, cosOuter, 0, castsShadow);
    }

    /** Hard ceilings on a deserialized light, well above any sane authored value. */
    private static final float MAX_RANGE = 256f;
    private static final float MAX_INTENSITY = 64f;
    /** Cosine of the widest cone the factories allow: an 89-degree half-angle. */
    private static final float MIN_CONE_COS = (float) Math.cos(Math.toRadians(89.0));

    /**
     * Whether every field lies in the range the public factories enforce. Deserialization
     * ({@link LightCodecs}) rebuilds a light through {@link #raw} and so bypasses those factories:
     * anything arriving from disk or from the network is unvalidated until it is checked here, and a
     * {@code NaN} or absurd value that reaches the renderer corrupts the frame for every player who
     * receives it. The server checks incoming lights with this before storing or broadcasting them.
     */
    public boolean isWellFormed() {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return false;
        if (!inUnitRange(r) || !inUnitRange(g) || !inUnitRange(b)) return false;
        if (!Float.isFinite(intensity) || intensity < 0f || intensity > MAX_INTENSITY) return false;
        if (!Float.isFinite(range) || range <= 0.05f || range > MAX_RANGE) return false;
        if (type == LightType.POINT) return true;
        if (!Float.isFinite(directionX) || !Float.isFinite(directionY) || !Float.isFinite(directionZ)) {
            return false;
        }
        float lengthSquared = directionX * directionX + directionY * directionY + directionZ * directionZ;
        if (Math.abs(lengthSquared - 1f) > 1e-3f) return false;
        return inCosineRange(cosInner) && cosOuter >= MIN_CONE_COS && cosInner > cosOuter;
    }

    private static boolean inUnitRange(float value) {
        return Float.isFinite(value) && value >= 0f && value <= 1f;
    }

    private static boolean inCosineRange(float value) {
        return Float.isFinite(value) && value >= -1f && value <= 1f;
    }

    /**
     * A copy of this light with {@link #castsShadow} set ({@code this} if unchanged).
     * A non-shadowed light never claims a slot from the shadow budget, so it is the
     * cheap way to spawn many lights: no bake, no per-frame shadow sampling.
     */
    public Light withShadow(boolean castsShadow) {
        if (this.castsShadow == castsShadow) return this;
        return new Light(type, x, y, z, directionX, directionY, directionZ,
                r, g, b, intensity, range,
                cosInner, cosOuter, goboTextureId, castsShadow);
    }

    /**
     * An omnidirectional point light.
     */
    public static Light point(double x, double y, double z,
                              float r, float g, float b, float intensity, float range) {
        validateCommon(x, y, z, r, g, b, intensity, range);
        return new Light(LightType.POINT, x, y, z, 0f, -1f, 0f,
                r, g, b, intensity, range, 1f, -1f, 0, true);
    }

    /**
     * A cone light. {@code innerAngleDeg <= outerAngleDeg} are half-angles in degrees.
     */
    public static Light spot(double x, double y, double z,
                             float dirX, float dirY, float dirZ,
                             float r, float g, float b, float intensity, float range,
                             float innerAngleDeg, float outerAngleDeg) {
        validateCommon(x, y, z, r, g, b, intensity, range);
        validateCone(innerAngleDeg, outerAngleDeg);
        float inverseLength = inverseDirectionLength(dirX, dirY, dirZ);
        return new Light(LightType.SPOT, x, y, z,
                dirX * inverseLength, dirY * inverseLength, dirZ * inverseLength,
                r, g, b, intensity, range,
                cos(innerAngleDeg), cos(outerAngleDeg), 0, true);
    }

    /**
     * A cone light modulated by a projected texture (gobo). The gobo's red channel
     * masks the cone; {@code goboTextureId} is a live GL texture id.
     */
    public static Light gobo(double x, double y, double z,
                             float dirX, float dirY, float dirZ,
                             float r, float g, float b, float intensity, float range,
                             float innerAngleDeg, float outerAngleDeg, int goboTextureId) {
        validateCommon(x, y, z, r, g, b, intensity, range);
        validateCone(innerAngleDeg, outerAngleDeg);
        if (goboTextureId <= 0) throw new IllegalArgumentException("Gobo texture id must be positive");
        float inverseLength = inverseDirectionLength(dirX, dirY, dirZ);
        return new Light(LightType.GOBO, x, y, z,
                dirX * inverseLength, dirY * inverseLength, dirZ * inverseLength,
                r, g, b, intensity, range,
                cos(innerAngleDeg), cos(outerAngleDeg), goboTextureId, true);
    }

    /**
     * A copy of this light repositioned (and, for a spot/gobo, re-aimed) at the given transform. The
     * direction is ignored for a point light. This is how a frame-sampled attachment rebuilds its light
     * each frame from the live block or entity it follows.
     */
    public Light at(double x, double y, double z, float directionX, float directionY, float directionZ) {
        requireFinite("position", x, y, z);
        float inverseLength = type == LightType.POINT ? 1f
                : inverseDirectionLength(directionX, directionY, directionZ);
        return new Light(type, x, y, z,
                type == LightType.POINT ? 0f : directionX * inverseLength,
                type == LightType.POINT ? -1f : directionY * inverseLength,
                type == LightType.POINT ? 0f : directionZ * inverseLength,
                r, g, b, intensity, range, cosInner, cosOuter, goboTextureId, castsShadow);
    }

    private static void validateCommon(double x, double y, double z,
                                       float r, float g, float b, float intensity, float range) {
        requireFinite("position", x, y, z);
        requireFinite("color", r, g, b);
        if (r < 0f || r > 1f || g < 0f || g > 1f || b < 0f || b > 1f) {
            throw new IllegalArgumentException("Light color components must be in [0, 1]");
        }
        if (!Float.isFinite(intensity) || intensity < 0f) {
            throw new IllegalArgumentException("Light intensity must be finite and non-negative");
        }
        if (!Float.isFinite(range) || range <= 0.05f) {
            throw new IllegalArgumentException("Light range must be finite and greater than 0.05");
        }
    }

    private static void validateCone(float inner, float outer) {
        if (!Float.isFinite(inner) || !Float.isFinite(outer)
                || inner < 0f || inner >= outer || outer >= 89f) {
            throw new IllegalArgumentException("Cone angles must satisfy 0 <= inner < outer < 89 degrees");
        }
    }

    private static float inverseDirectionLength(float x, float y, float z) {
        requireFinite("direction", x, y, z);
        float lengthSquared = x * x + y * y + z * z;
        if (lengthSquared < 1e-8f) throw new IllegalArgumentException("Light direction must be non-zero");
        return (float) (1.0 / Math.sqrt(lengthSquared));
    }

    private static void requireFinite(String name, double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Light " + name + " must be finite");
        }
    }

    private static float cos(float degrees) {
        return (float) Math.cos(Math.toRadians(degrees));
    }
}
