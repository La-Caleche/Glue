package fr.lacaleche.glue.client.render.light;

/** Mutable allocation-free target populated by a {@link LightAttachment}. */
public final class LightTransform {

    double x;
    double y;
    double z;
    float directionX;
    float directionY = -1f;
    float directionZ;

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
