package fr.lacaleche.glue.client.render.gizmo;

import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * Where a gizmo's target stands: a position, a rotation, and a scale along the rotated axes. The position
 * is in the coordinates of the {@link GizmoView} the gizmo is used with, world coordinates in the world.
 * The vectors are copied in and out, so a pose never changes once made.
 */
public record GizmoPose(Vector3d position, Quaternionf rotation, Vector3f scale) {

    public GizmoPose {
        position = new Vector3d(position);
        rotation = new Quaternionf(rotation);
        scale = new Vector3f(scale);
    }

    /** A pose at the position, unrotated and unscaled. */
    public static GizmoPose at(double x, double y, double z) {
        return new GizmoPose(new Vector3d(x, y, z), new Quaternionf(), new Vector3f(1.0F));
    }

    @Override
    public Vector3d position() {
        return new Vector3d(this.position);
    }

    @Override
    public Quaternionf rotation() {
        return new Quaternionf(this.rotation);
    }

    @Override
    public Vector3f scale() {
        return new Vector3f(this.scale);
    }

    public GizmoPose withPosition(Vector3d position) {
        return new GizmoPose(position, this.rotation, this.scale);
    }

    public GizmoPose withRotation(Quaternionf rotation) {
        return new GizmoPose(this.position, rotation, this.scale);
    }

    public GizmoPose withScale(Vector3f scale) {
        return new GizmoPose(this.position, this.rotation, scale);
    }
}
