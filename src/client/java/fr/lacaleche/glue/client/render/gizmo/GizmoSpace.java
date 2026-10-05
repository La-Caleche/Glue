package fr.lacaleche.glue.client.render.gizmo;

/**
 * Which axes a {@link Gizmo} translates and rotates along: the target's own, rotated with it, or the
 * world's. Scaling always follows the target's own axes.
 */
public enum GizmoSpace {
    LOCAL, WORLD
}
