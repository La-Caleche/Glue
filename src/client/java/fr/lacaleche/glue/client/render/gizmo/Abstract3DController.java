package fr.lacaleche.glue.client.render.gizmo;

import fr.lacaleche.glue.history.HistoryManager;

public abstract class Abstract3DController {

    protected final AbstractGizmoController gizmoController;
    protected final HistoryManager historyManager;
    protected boolean wasUsingGizmo = false;

    public Abstract3DController(AbstractGizmoController gizmoController) {
        this.gizmoController = gizmoController;
        this.historyManager = new HistoryManager();
    }

    public AbstractGizmoController getGizmoController() {
        return gizmoController;
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public void undo() {
        historyManager.undo();
    }

    public void redo() {
        historyManager.redo();
    }

    public void updateGizmoInteraction() {
        boolean isUsing = gizmoController.isDragging();

        if (isUsing && !wasUsingGizmo) {
            onGizmoDragStart();
        } else if (!isUsing && wasUsingGizmo) {
            onGizmoDragEnd();
        }

        wasUsingGizmo = isUsing;
    }

    protected abstract void onGizmoDragStart();

    protected abstract void onGizmoDragEnd();

    public abstract void applyGizmoTransform();
}
