package fr.lacaleche.glue.lumos;

/** Mutable allocation-free target populated by a {@link LightAttachment}. */
public final class LightTransform {

    public double x;
    public double y;
    public double z;
    public float directionX;
    public float directionY = -1f;
    public float directionZ;

    public LightTransform position(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public LightTransform direction(float x, float y, float z) {
        directionX = x;
        directionY = y;
        directionZ = z;
        return this;
    }
}
