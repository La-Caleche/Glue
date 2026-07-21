package fr.lacaleche.glue.client.render.gizmo;

/**
 * Abstraction for the 2D rendering backend used by GizmoRenderer.
 * This allows GizmoRenderer to be agnostic of the underlying rendering library.
 */
public interface GizmoRenderBackend {
    void drawLine(float x1, float y1, float x2, float y2, int color, float thickness);

    void drawTriangle(float x1, float y1, float x2, float y2, float x3, float y3, int color);

    void drawQuad(float x1, float y1, float x2, float y2, float x3, float y3, float x4, float y4, int color);
}
