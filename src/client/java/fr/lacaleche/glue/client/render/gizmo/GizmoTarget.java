package fr.lacaleche.glue.client.render.gizmo;

/**
 * What a {@link Gizmo} moves. The gizmo reads {@link #pose} every frame it draws, so the target may move
 * on its own between drags. A drag calls {@link #preview} with each new pose, then {@link #commit} once
 * on release with the poses before and after; a cancelled drag previews the pose before instead. All on
 * the client thread.
 */
public interface GizmoTarget {

    /** Where the target stands now. */
    GizmoPose pose();

    /** Shows the target at the pose while a drag goes on. */
    void preview(GizmoPose pose);

    /**
     * Ends a drag that changed the pose, for the target to keep {@code after}, record it for undo, or send
     * it on. Not called for a drag that left the pose as it was.
     */
    void commit(GizmoPose before, GizmoPose after);

    /** Whether the target can be changed by the operation; the gizmo draws nothing for one it cannot. */
    default boolean supports(GizmoOperation operation) {
        return true;
    }
}
