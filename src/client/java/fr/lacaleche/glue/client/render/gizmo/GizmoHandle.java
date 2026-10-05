package fr.lacaleche.glue.client.render.gizmo;

/**
 * A part of a {@link Gizmo} that can be grabbed. Which ones are drawn depends on the operation: the axes
 * everywhere, the planes and the centre when translating, the view ring when rotating, and the centre,
 * for a uniform scale, when scaling.
 */
public enum GizmoHandle {
    X, Y, Z, XY, YZ, XZ, CENTER, VIEW;

    /** The axis an axis handle moves along, or a plane handle's normal: 0, 1 or 2; -1 for the others. */
    int axis() {
        return switch (this) {
            case X, YZ -> 0;
            case Y, XZ -> 1;
            case Z, XY -> 2;
            case CENTER, VIEW -> -1;
        };
    }

    boolean isAxis() {
        return this == X || this == Y || this == Z;
    }

    boolean isPlane() {
        return this == XY || this == YZ || this == XZ;
    }
}
