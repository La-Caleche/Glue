package fr.lacaleche.glue.client.render.gizmo;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GizmoTest {

    /** Five blocks in front of a target at the origin, looking down -Z, so X points right and Y up on screen. */
    private static final GizmoView VIEW = new GizmoView(new Vector3d(0.0, 0.0, 5.0), new Matrix4f(),
            new Matrix4f().setPerspective((float) Math.toRadians(70.0), 640.0F / 360.0F, 0.05F, 100.0F),
            0.0F, 0.0F, 640.0F, 360.0F);

    private final Target target = new Target();
    private final Gizmo gizmo = new Gizmo();

    GizmoTest() {
        this.gizmo.setTarget(this.target);
    }

    @Test
    void draggingTheXArrowMovesTheTargetAlongXOnly() {
        Vector2f arrow = this.grab(GizmoHandle.X);

        this.gizmo.drag(VIEW, arrow.x + 40.0, arrow.y, false);
        this.gizmo.release();

        Vector3d position = this.target.pose.position();
        assertTrue(position.x > 0.1, "moved along X: " + position);
        assertEquals(0.0, position.y, 1.0E-6);
        assertEquals(0.0, position.z, 1.0E-6);
        assertEquals(1, this.target.commits.size());
        assertFalse(this.gizmo.isDragging());
    }

    @Test
    void snappingRoundsATranslationToWholeSteps() {
        this.gizmo.setSnapping(true);
        Vector2f arrow = this.grab(GizmoHandle.X);

        this.gizmo.drag(VIEW, arrow.x + 57.0, arrow.y, false);

        double x = this.target.pose.position().x;
        assertTrue(x > 0.0);
        assertEquals(0.0, Math.IEEEremainder(x, this.gizmo.translationStep()), 1.0E-6);
    }

    @Test
    void aHeldKeyInvertsSnapping() {
        Vector2f arrow = this.grab(GizmoHandle.X);

        this.gizmo.drag(VIEW, arrow.x + 57.0, arrow.y, true);

        assertEquals(0.0, Math.IEEEremainder(this.target.pose.position().x, this.gizmo.translationStep()), 1.0E-6);
    }

    @Test
    void draggingAPlaneLeavesItsNormalAlone() {
        Vector2f plane = this.grab(GizmoHandle.XY);

        this.gizmo.drag(VIEW, plane.x + 30.0, plane.y - 20.0, false);

        Vector3d position = this.target.pose.position();
        assertTrue(position.x > 0.0 && position.y > 0.0, "moved in XY: " + position);
        assertEquals(0.0, position.z, 1.0E-6);
    }

    @Test
    void turningAroundTheRingFacingTheCameraRotatesCounterclockwiseAsSeen() {
        this.gizmo.setOperation(GizmoOperation.ROTATE);
        this.gizmo.setSnapping(true);
        Vector2f ring = this.grab(GizmoHandle.Z);
        float radius = ring.x - 320.0F;
        assertTrue(radius > 0.0F && Math.abs(ring.y - 180.0F) < 1.0E-3F, "grabbed right of the centre: " + ring);

        this.gizmo.drag(VIEW, 320.0, 180.0 - radius, false);

        Vector3f x = this.target.pose.rotation().transform(new Vector3f(1.0F, 0.0F, 0.0F));
        assertEquals(0.0F, x.x, 1.0E-4F);
        assertEquals(1.0F, x.y, 1.0E-4F);
        assertEquals(0.0F, x.z, 1.0E-4F);
    }

    @Test
    void draggingAScaleCubeStretchesThatAxisOnly() {
        this.gizmo.setOperation(GizmoOperation.SCALE);
        Vector2f cube = this.grab(GizmoHandle.X);

        this.gizmo.drag(VIEW, cube.x + 40.0, cube.y, false);

        Vector3f scale = this.target.pose.scale();
        assertTrue(scale.x > 1.1F, "stretched: " + scale);
        assertEquals(1.0F, scale.y);
        assertEquals(1.0F, scale.z);
    }

    @Test
    void cancellingPutsTheTargetBackWithoutACommit() {
        Vector2f arrow = this.grab(GizmoHandle.X);
        this.gizmo.drag(VIEW, arrow.x + 40.0, arrow.y, false);

        this.gizmo.cancel();

        assertEquals(GizmoPose.at(0.0, 0.0, 0.0), this.target.pose);
        assertTrue(this.target.commits.isEmpty());
        assertFalse(this.gizmo.isDragging());
    }

    @Test
    void releasingWithoutMovingCommitsNothing() {
        Vector2f arrow = this.grab(GizmoHandle.X);

        this.gizmo.drag(VIEW, arrow.x, arrow.y, false);
        this.gizmo.release();

        assertTrue(this.target.commits.isEmpty());
    }

    @Test
    void hoverFindsTheArrowUnderThePointerAndNothingElsewhere() {
        Vector2f arrow = this.gizmo.grabPoint(VIEW, GizmoHandle.Y);
        assertNotNull(arrow);

        assertTrue(this.gizmo.hover(VIEW, arrow.x + 2.0, arrow.y));
        assertEquals(GizmoHandle.Y, this.gizmo.hovered());
        assertFalse(this.gizmo.hover(VIEW, 5.0, 5.0));
        assertNull(this.gizmo.hovered());
    }

    @Test
    void anAxisSeenEndOnCannotBeGrabbed() {
        assertNull(this.gizmo.grabPoint(VIEW, GizmoHandle.Z));
    }

    @Test
    void aViewThatCarriesTheCameraPositionDrawsTheSameGizmo() {
        GizmoView absolute = new GizmoView(new Vector3d(), new Matrix4f().translation(0.0F, 0.0F, -5.0F),
                new Matrix4f().setPerspective((float) Math.toRadians(70.0), 640.0F / 360.0F, 0.05F, 100.0F),
                0.0F, 0.0F, 640.0F, 360.0F);

        Vector2f expected = this.gizmo.grabPoint(VIEW, GizmoHandle.X);
        Vector2f actual = this.gizmo.grabPoint(absolute, GizmoHandle.X);

        assertNotNull(actual);
        assertEquals(expected.x, actual.x, 1.0E-3F);
        assertEquals(expected.y, actual.y, 1.0E-3F);
    }

    @Test
    void changingTheTargetCancelsTheDrag() {
        Vector2f arrow = this.grab(GizmoHandle.X);
        this.gizmo.drag(VIEW, arrow.x + 40.0, arrow.y, false);

        this.gizmo.setTarget(null);

        assertFalse(this.gizmo.isDragging());
        assertEquals(GizmoPose.at(0.0, 0.0, 0.0), this.target.pose);
    }

    @Test
    void stepsMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> this.gizmo.setSteps(0.0, 15.0, 0.25));
    }

    private Vector2f grab(GizmoHandle handle) {
        Vector2f point = this.gizmo.grabPoint(VIEW, handle);
        assertNotNull(point, handle + " is drawn");
        assertTrue(this.gizmo.press(VIEW, point.x, point.y), handle + " takes the press");
        assertEquals(handle, this.gizmo.hovered());
        return point;
    }

    private static final class Target implements GizmoTarget {

        private final List<GizmoPose> commits = new ArrayList<>();
        private GizmoPose pose = GizmoPose.at(0.0, 0.0, 0.0);

        @Override
        public GizmoPose pose() {
            return this.pose;
        }

        @Override
        public void preview(GizmoPose pose) {
            this.pose = pose;
        }

        @Override
        public void commit(GizmoPose before, GizmoPose after) {
            this.pose = after;
            this.commits.add(after);
        }
    }
}
