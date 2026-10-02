package fr.lacaleche.glue.client.render.gizmo;

import fr.lacaleche.glue.data.components.TransformationComponent;
import org.joml.*;

import java.lang.Math;
import java.util.Arrays;

/**
 * Platform-agnostic Gizmo Controller.
 * Handles core logic for gizmo manipulation/math.
 * Input and Rendering are abstracted via subclasses.
 */
public abstract class AbstractGizmoController {

    // Transform Data
    protected final Vector3f currentTranslation = new Vector3f();
    protected final Quaternionf currentLeftRotation = new Quaternionf();
    protected final Vector3f currentScale = new Vector3f(1, 1, 1);
    protected final Quaternionf currentRightRotation = new Quaternionf();
    // Snap
    protected final float[] snapValues = new float[]{1f, 1f, 1f};
    // Gizmo State
    protected final GizmoRenderer renderer = new GizmoRenderer();
    protected final Vector2f dragStartMouse = new Vector2f();
    protected final Matrix4f dragStartMatrix = new Matrix4f();
    // State
    protected GizmoOperation currentOperation = GizmoOperation.TRANSLATE;
    protected GizmoSpace currentMode = GizmoSpace.LOCAL;
    // Interaction State
    protected float currentInteractionScaleFactor = 1.0f;
    protected boolean useSnap = false;
    protected GizmoAxis activeAxis = GizmoAxis.NONE;
    protected GizmoAxis hoveredAxis = GizmoAxis.NONE;
    protected boolean isDragging = false;
    protected float dragRotationSign = 1.0f;

    // Abstract Input Methods
    protected abstract float getMouseX();

    protected abstract float getMouseY();

    protected abstract boolean isLeftMouseDown();

    protected abstract boolean isLeftMouseClicked();

    protected abstract boolean isShiftDown();

    protected abstract boolean isControlDown();

    protected abstract GizmoRenderBackend getRenderBackend();

    public float[] getSnapValues() {
        return snapValues;
    }

    public GizmoOperation getCurrentOperation() {
        return currentOperation;
    }

    public GizmoSpace getCurrentMode() {
        return currentMode;
    }

    public boolean isDragging() {
        return isDragging;
    }

    public boolean isHovered() {
        return hoveredAxis != GizmoAxis.NONE;
    }

    public boolean isUsingSnap() {
        return useSnap;
    }

    public void setUseSnap(boolean useSnap) {
        this.useSnap = useSnap;
    }

    public void setOperation(GizmoOperation operation) {
        this.currentOperation = operation;
    }

    public void setMode(GizmoSpace mode) {
        this.currentMode = mode;
    }

    public void updateSnapValues(float v) {
        Arrays.fill(snapValues, v);
    }

    public void resetTransform() {
        currentTranslation.set(0, 0, 0);
        currentLeftRotation.identity();
        currentScale.set(1, 1, 1);
        currentRightRotation.identity();
    }

    public void recomposeMatrix(TransformationComponent transform) {
        currentTranslation.set(transform.translation());
        currentLeftRotation.set(transform.leftRotation());
        currentScale.set(transform.scale());
        currentRightRotation.set(transform.rightRotation());
    }

    public Matrix4f buildMatrix() {
        return new Matrix4f()
                .translate(currentTranslation)
                .rotate(currentLeftRotation)
                .scale(currentScale)
                .rotate(currentRightRotation);
    }

    public Vector3f getTranslation() {
        return currentTranslation;
    }

    public Quaternionf getLeftRotation() {
        return currentLeftRotation;
    }

    public Vector3f getScale() {
        return currentScale;
    }

    public Quaternionf getRightRotation() {
        return currentRightRotation;
    }

    public void manipulate(float[] viewMatrixArr, float[] projectionMatrixArr,
                           float viewportX, float viewportY,
                           float viewportWidth, float viewportHeight, boolean inputEnabled) {

        Matrix4f viewMatrix = new Matrix4f().set(viewMatrixArr);
        Matrix4f projMatrix = new Matrix4f().set(projectionMatrixArr);

        renderer.updateContext(viewMatrixArr, projectionMatrixArr,
                viewportX, viewportY, viewportWidth, viewportHeight);

        Vector3f origin = new Vector3f(currentTranslation);
        Vector3f xDir = new Vector3f(1, 0, 0);
        Vector3f yDir = new Vector3f(0, 1, 0);
        Vector3f zDir = new Vector3f(0, 0, 1);

        if (currentMode == GizmoSpace.LOCAL) {
            Quaternionf netRot = new Quaternionf(currentLeftRotation).mul(currentRightRotation);
            xDir.rotate(netRot);
            yDir.rotate(netRot);
            zDir.rotate(netRot);
        }

        if (inputEnabled || isDragging) {
            handleInteraction(viewMatrix, projMatrix, viewportX, viewportY, viewportWidth, viewportHeight,
                    origin, xDir, yDir, zDir);
        } else {
            hoveredAxis = GizmoAxis.NONE;
        }

        renderGizmo(origin, xDir, yDir, zDir, viewMatrix, viewportX, viewportY, viewportWidth, viewportHeight);
    }

    private void handleInteraction(Matrix4f view, Matrix4f proj, float vx, float vy, float vw, float vh,
                                   Vector3f origin, Vector3f xDir, Vector3f yDir, Vector3f zDir) {
        float mouseX = getMouseX();
        float mouseY = getMouseY();
        Vector2f mousePos = new Vector2f(mouseX, mouseY);

        if (mouseX < vx || mouseX > vx + vw || mouseY < vy || mouseY > vy + vh) {
            if (!isDragging) {
                hoveredAxis = GizmoAxis.NONE;
                return;
            }
        }

        if (isDragging) {
            if (isLeftMouseDown()) {
                updateDrag(mousePos, origin, xDir, yDir, zDir, vx, vy, vw, vh, view, proj);
            } else {
                isDragging = false;
                activeAxis = GizmoAxis.NONE;
                currentInteractionScaleFactor = 1.0f;
            }
        } else {
            hoveredAxis = pickHandle(mousePos, origin, xDir, yDir, zDir, view, proj, vx, vy, vw, vh);

            if (hoveredAxis != GizmoAxis.NONE && isLeftMouseClicked()) {
                isDragging = true;
                activeAxis = hoveredAxis;
                dragStartMouse.set(mousePos);
                dragStartMatrix.set(buildMatrix());
                currentInteractionScaleFactor = 1.0f;

                if (currentOperation == GizmoOperation.ROTATE) {
                    Vector3f axis = new Vector3f();
                    if (activeAxis == GizmoAxis.X) axis.set(xDir);
                    else if (activeAxis == GizmoAxis.Y) axis.set(yDir);
                    else if (activeAxis == GizmoAxis.Z) axis.set(zDir);

                    Vector3f viewBack = new Vector3f(view.m02(), view.m12(), view.m22());
                    dragRotationSign = (axis.dot(viewBack) > 0) ? -1.0f : 1.0f;
                }
            }
        }
    }

    private float getHandleLength(Vector3f origin, Matrix4f view) {
        Vector4f p = new Vector4f(origin, 1.0f).mul(view);
        return Math.max(Math.abs(p.z) * 0.1f, 0.01f);
    }

    private void renderGizmo(Vector3f origin, Vector3f xDir, Vector3f yDir, Vector3f zDir, Matrix4f view,
                             float viewportX, float viewportY, float viewportWidth, float viewportHeight) {
        GizmoRenderBackend backend = getRenderBackend();
        if (backend == null) return;

        float len = getHandleLength(origin, view);
        float circleRadius = len * 0.7f;

        int baseColX = 0xFF5050F0, baseColY = 0xFF50F050, baseColZ = 0xFFF05050;
        int hoverColX = 0xFFAAAAFF, hoverColY = 0xFFAAFFAA, hoverColZ = 0xFFFFAAAA;
        int activeCol = 0xFFFFFFFF;

        boolean xHover = hoveredAxis == GizmoAxis.X, yHover = hoveredAxis == GizmoAxis.Y, zHover = hoveredAxis == GizmoAxis.Z;
        boolean xDrag = activeAxis == GizmoAxis.X, yDrag = activeAxis == GizmoAxis.Y, zDrag = activeAxis == GizmoAxis.Z;

        int colX = xDrag ? activeCol : (xHover ? hoverColX : baseColX);
        int colY = yDrag ? activeCol : (yHover ? hoverColY : baseColY);
        int colZ = zDrag ? activeCol : (zHover ? hoverColZ : baseColZ);

        if (currentOperation == GizmoOperation.TRANSLATE || currentOperation == GizmoOperation.SCALE) {
            float lenX = len, lenY = len, lenZ = len;

            if (currentOperation == GizmoOperation.SCALE && isDragging) {
                boolean allAxis = isControlDown();
                lenX *= (activeAxis == GizmoAxis.X || allAxis) ? currentInteractionScaleFactor : 1f;
                lenY *= (activeAxis == GizmoAxis.Y || allAxis) ? currentInteractionScaleFactor : 1f;
                lenZ *= (activeAxis == GizmoAxis.Z || allAxis) ? currentInteractionScaleFactor : 1f;

                if (Math.abs(currentInteractionScaleFactor - 1.0f) > 0.1f) {
                    int ghostAlpha = 0x55000000;
                    renderer.drawLine(backend, origin, new Vector3f(xDir).mul(len).add(origin), colX & 0x00FFFFFF | ghostAlpha, 2.0f);
                    renderer.drawLine(backend, origin, new Vector3f(yDir).mul(len).add(origin), colY & 0x00FFFFFF | ghostAlpha, 2.0f);
                    renderer.drawLine(backend, origin, new Vector3f(zDir).mul(len).add(origin), colZ & 0x00FFFFFF | ghostAlpha, 2.0f);
                }
            }

            Vector3f xEnd = new Vector3f(xDir).mul(lenX).add(origin);
            Vector3f yEnd = new Vector3f(yDir).mul(lenY).add(origin);
            Vector3f zEnd = new Vector3f(zDir).mul(lenZ).add(origin);

            renderer.drawLine(backend, origin, xEnd, colX, (xHover || xDrag) ? 5.0f : 3.0f);
            renderer.drawLine(backend, origin, yEnd, colY, (yHover || yDrag) ? 5.0f : 3.0f);
            renderer.drawLine(backend, origin, zEnd, colZ, (zHover || zDrag) ? 5.0f : 3.0f);

            if (currentOperation == GizmoOperation.TRANSLATE) {
                float coneLen = len * 0.2f;
                float coneRadius = coneLen * 0.25f;
                renderer.drawCone(backend, xEnd, coneLen, coneRadius, xDir, colX);
                renderer.drawCone(backend, yEnd, coneLen, coneRadius, yDir, colY);
                renderer.drawCone(backend, zEnd, coneLen, coneRadius, zDir, colZ);
            } else {
                float boxSize = len * 0.08f;
                renderer.drawOrientedCube(backend, xEnd, xDir, yDir, zDir, boxSize, colX);
                renderer.drawOrientedCube(backend, yEnd, xDir, yDir, zDir, boxSize, colY);
                renderer.drawOrientedCube(backend, zEnd, xDir, yDir, zDir, boxSize, colZ);
            }
        } else if (currentOperation == GizmoOperation.ROTATE) {
            renderer.drawCircle(backend, origin, circleRadius, xDir, colX, 32);
            renderer.drawCircle(backend, origin, circleRadius, yDir, colY, 32);
            renderer.drawCircle(backend, origin, circleRadius, zDir, colZ, 32);
        }
    }

    private GizmoAxis pickHandle(Vector2f mouse, Vector3f origin, Vector3f xDir, Vector3f yDir, Vector3f zDir,
                                 Matrix4f view, Matrix4f proj, float vx, float vy, float vw, float vh) {
        float handleLen = getHandleLength(origin, view);

        if (currentOperation == GizmoOperation.ROTATE) {
            float circleRadius = handleLen * 0.7f;
            float threshold = 0.2f * handleLen;
            if (GizmoMath.checkProjectedRing(mouse.x, mouse.y, origin, xDir, circleRadius, threshold, vx, vy, vw, vh, view, proj))
                return GizmoAxis.X;
            if (GizmoMath.checkProjectedRing(mouse.x, mouse.y, origin, yDir, circleRadius, threshold, vx, vy, vw, vh, view, proj))
                return GizmoAxis.Y;
            if (GizmoMath.checkProjectedRing(mouse.x, mouse.y, origin, zDir, circleRadius, threshold, vx, vy, vw, vh, view, proj))
                return GizmoAxis.Z;
            return GizmoAxis.NONE;
        }

        Vector2f pStart = new Vector2f();
        if (!GizmoMath.project(origin, view, proj, vx, vy, vw, vh, pStart)) return GizmoAxis.NONE;

        float thresholdSq = 200.0f;
        if (GizmoMath.checkProjectedAxis(mouse, pStart, origin, xDir, handleLen, view, proj, vx, vy, vw, vh, thresholdSq))
            return GizmoAxis.X;
        if (GizmoMath.checkProjectedAxis(mouse, pStart, origin, yDir, handleLen, view, proj, vx, vy, vw, vh, thresholdSq))
            return GizmoAxis.Y;
        if (GizmoMath.checkProjectedAxis(mouse, pStart, origin, zDir, handleLen, view, proj, vx, vy, vw, vh, thresholdSq))
            return GizmoAxis.Z;
        return GizmoAxis.NONE;
    }

    private void updateDrag(Vector2f mousePos, Vector3f origin, Vector3f xDir, Vector3f yDir, Vector3f zDir,
                            float vx, float vy, float vw, float vh, Matrix4f view, Matrix4f proj) {
        Vector3f axis = new Vector3f();
        if (activeAxis == GizmoAxis.X) axis.set(xDir);
        else if (activeAxis == GizmoAxis.Y) axis.set(yDir);
        else if (activeAxis == GizmoAxis.Z) axis.set(zDir);
        else return;

        Vector3f rayOrigin = new Vector3f(), rayDir = new Vector3f();
        GizmoMath.getRayFromScreen(mousePos.x, mousePos.y, vx, vy, vw, vh, view, proj, rayOrigin, rayDir);
        Vector3f startRayOrigin = new Vector3f(), startRayDir = new Vector3f();
        GizmoMath.getRayFromScreen(dragStartMouse.x, dragStartMouse.y, vx, vy, vw, vh, view, proj, startRayOrigin, startRayDir);

        Vector3f planeNormal = new Vector3f(view.m02(), view.m12(), view.m22());
        boolean doSnap = useSnap || isShiftDown();

        if (currentOperation == GizmoOperation.TRANSLATE) {
            Vector3f sideVec = new Vector3f(rayDir).cross(axis);
            Vector3f optimalNormal = new Vector3f();
            if (sideVec.lengthSquared() > 1e-6f) optimalNormal.set(sideVec).cross(axis).normalize();
            else optimalNormal.set(planeNormal);
            if (!Float.isFinite(optimalNormal.x) || optimalNormal.lengthSquared() < 1e-6f)
                optimalNormal.set(planeNormal);

            Vector3f intersectCurr = new Vector3f(), intersectStart = new Vector3f();
            if (GizmoMath.intersectRayPlane(rayOrigin, rayDir, origin, optimalNormal, intersectCurr) &&
                    GizmoMath.intersectRayPlane(startRayOrigin, startRayDir, origin, optimalNormal, intersectStart)) {
                float projDelta = new Vector3f(intersectCurr).sub(intersectStart).dot(axis);
                if (doSnap && snapValues[0] > 0) projDelta = Math.round(projDelta / snapValues[0]) * snapValues[0];
                if (Float.isFinite(projDelta)) applyTranslate(projDelta, axis);
            }

        } else if (currentOperation == GizmoOperation.SCALE) {
            Vector2f p1 = new Vector2f(), p2 = new Vector2f();
            if (GizmoMath.project(origin, view, proj, vx, vy, vw, vh, p1) &&
                    GizmoMath.project(new Vector3f(origin).add(axis), view, proj, vx, vy, vw, vh, p2)) {
                Vector2f axisVec = new Vector2f(p2).sub(p1);
                if (axisVec.lengthSquared() < 1e-6f) axisVec.set(1, 0);
                axisVec.normalize();
                float projDelta = new Vector2f(mousePos).sub(dragStartMouse).dot(axisVec);
                float scaleFactor = (float) Math.exp(projDelta * 0.005f);
                if (doSnap && snapValues[2] > 0) {
                    scaleFactor = Math.round(scaleFactor / snapValues[2]) * snapValues[2];
                    if (Math.abs(scaleFactor) < 1e-6f) scaleFactor = 0f;
                }
                if (Float.isFinite(scaleFactor)) {
                    currentInteractionScaleFactor = scaleFactor;
                    applyScale(scaleFactor);
                }
            }

        } else if (currentOperation == GizmoOperation.ROTATE) {
            Vector2f screenOrigin = new Vector2f();
            if (!GizmoMath.project(origin, view, proj, vx, vy, vw, vh, screenOrigin)) return;
            Vector2f vecStart = new Vector2f(dragStartMouse).sub(screenOrigin);
            Vector2f vecCurr = new Vector2f(mousePos).sub(screenOrigin);
            if (vecStart.lengthSquared() < 1e-6f || vecCurr.lengthSquared() < 1e-6f) return;
            double angleDelta = getAngleDelta(vecStart, vecCurr, doSnap);
            if (Double.isFinite(angleDelta)) applyRotate((float) angleDelta, axis);
        }
    }

    private double getAngleDelta(Vector2f vecStart, Vector2f vecCurr, boolean doSnap) {
        double angleDelta = Math.atan2(vecCurr.y, vecCurr.x) - Math.atan2(vecStart.y, vecStart.x);
        if (angleDelta > Math.PI) angleDelta -= 2 * Math.PI;
        if (angleDelta < -Math.PI) angleDelta += 2 * Math.PI;
        angleDelta *= dragRotationSign;
        if (doSnap && snapValues[1] > 0) {
            double snapRad = Math.toRadians(snapValues[1]);
            angleDelta = Math.round(angleDelta / snapRad) * snapRad;
        }
        return angleDelta;
    }

    private void applyTranslate(float delta, Vector3f axisDir) {
        if (!Float.isFinite(delta)) return;
        Matrix4f m = new Matrix4f(dragStartMatrix);
        Vector3f t = new Vector3f();
        m.getTranslation(t);
        t.add(new Vector3f(axisDir).mul(delta));
        m.setTranslation(t);
        decomposeAndApply(m);
    }

    private void applyRotate(float angle, Vector3f axisDir) {
        if (!Float.isFinite(angle)) return;
        Matrix4f m = new Matrix4f(dragStartMatrix);
        Vector3f t = new Vector3f();
        m.getTranslation(t);
        m.setTranslation(0, 0, 0);
        if (currentMode == GizmoSpace.LOCAL) {
            if (activeAxis == GizmoAxis.X) m.rotateX(angle);
            if (activeAxis == GizmoAxis.Y) m.rotateY(angle);
            if (activeAxis == GizmoAxis.Z) m.rotateZ(angle);
        } else {
            m = new Matrix4f().rotate(angle, axisDir).mul(m);
        }
        m.setTranslation(t);
        decomposeAndApply(m);
    }

    private void applyScale(float factor) {
        if (!Float.isFinite(factor)) return;
        boolean allAxis = isControlDown();
        Matrix4f m = new Matrix4f(dragStartMatrix);
        Vector3f t = new Vector3f();
        m.getTranslation(t);
        m.setTranslation(0, 0, 0);
        float sx = (activeAxis == GizmoAxis.X || allAxis) ? factor : 1.0f;
        float sy = (activeAxis == GizmoAxis.Y || allAxis) ? factor : 1.0f;
        float sz = (activeAxis == GizmoAxis.Z || allAxis) ? factor : 1.0f;
        if (currentMode == GizmoSpace.LOCAL) m.scale(sx, sy, sz);
        else m.scaleLocal(sx, sy, sz);
        m.setTranslation(t);
        decomposeAndApply(m);
    }

    private void decomposeAndApply(Matrix4f m) {
        if (!Float.isFinite(m.m00()) || !Float.isFinite(m.m33())) return;
        // Write to temporaries so state is never partially mutated on failure
        Vector3f t = new Vector3f();
        Quaternionf lr = new Quaternionf();
        Vector3f s = new Vector3f();
        Quaternionf rr = new Quaternionf();
        try {
            GizmoMath.decomposeMatrix(m, t, lr, s, rr);
            currentTranslation.set(t);
            currentLeftRotation.set(lr);
            currentScale.set(s);
            currentRightRotation.set(rr);
        } catch (Exception e) {
            // keep previous state if decomposition fails
        }
    }

    public enum GizmoAxis {
        NONE, X, Y, Z
    }
}
