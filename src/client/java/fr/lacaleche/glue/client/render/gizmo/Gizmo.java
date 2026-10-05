package fr.lacaleche.glue.client.render.gizmo;

import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.List;

/**
 * A translate, rotate and scale handle drawn around a {@link GizmoTarget}, at a constant size on screen.
 * The caller routes the pointer to it through a {@link GizmoView}: {@link #hover} as the pointer moves,
 * {@link #press} on a click, {@link #drag} while the button is held, and {@link #release} or
 * {@link #cancel} at the end. {@link fr.lacaleche.glue.client.ui.UiPanelScreen#setGizmo} does this for
 * the world. The gizmo draws itself into a screen with {@link #render}, or into the world while
 * {@link WorldGizmos#show shown}.
 *
 * <p>Translation follows the grabbed axis, plane or, from the centre, the plane facing the camera.
 * Rotation follows the pointer's angle around the centre, about the grabbed ring's axis or, on the outer
 * ring, the view's. Scaling stretches along one axis, or all three from the centre. Snapping rounds a
 * translation and a rotation to whole steps of the change, and a scale to whole steps of its value.
 * Client thread only.</p>
 */
public final class Gizmo {

    private static final List<GizmoHandle> TRANSLATE_HANDLES = List.of(GizmoHandle.CENTER, GizmoHandle.XY,
            GizmoHandle.YZ, GizmoHandle.XZ, GizmoHandle.X, GizmoHandle.Y, GizmoHandle.Z);
    private static final List<GizmoHandle> ROTATE_HANDLES = List.of(GizmoHandle.X, GizmoHandle.Y, GizmoHandle.Z,
            GizmoHandle.VIEW);
    private static final List<GizmoHandle> SCALE_HANDLES = List.of(GizmoHandle.CENTER, GizmoHandle.X, GizmoHandle.Y,
            GizmoHandle.Z);
    private static final int[] AXIS_COLORS = {0xFFE5484D, 0xFF6BC04B, 0xFF3E86F0};
    private static final int ACTIVE_COLOR = 0xFFFFD23F;
    private static final int CENTER_COLOR = 0xFFF0F0F0;
    private static final int VIEW_RING_COLOR = 0xFFC8C8C8;
    private static final int PLANE_ALPHA = 0xA0;
    private static final float PICK_PIXELS = 8.0F;
    /** Pixels added to an edge-on ring's distance, so the ring facing the camera wins where they cross. */
    private static final float EDGE_ON_BIAS = 3.0F;
    private static final int RING_SEGMENTS = 64;
    private static final float SHAFT_START = 0.2F;
    private static final float SHAFT_END = 0.78F;
    private static final float SHAFT_RADIUS = 0.018F;
    private static final float CONE_RADIUS = 0.065F;
    private static final float SCALE_CUBE = 0.92F;
    private static final float SCALE_CUBE_HALF = 0.06F;
    private static final float CENTER_HALF = 0.075F;
    private static final float PLANE_NEAR = 0.3F;
    private static final float PLANE_FAR = 0.5F;
    private static final float RING_RADIUS = 1.0F;
    private static final float RING_THICKNESS = 0.016F;
    private static final float VIEW_RING_RADIUS = 1.2F;
    private static final float MIN_SCALE = 0.01F;

    private @Nullable GizmoTarget target;
    private GizmoOperation operation = GizmoOperation.TRANSLATE;
    private GizmoSpace space = GizmoSpace.WORLD;
    private boolean snapping;
    private double translationStep = 0.5;
    private double rotationStep = 15.0;
    private double scaleStep = 0.25;
    private int size = 96;
    private @Nullable GizmoHandle hovered;
    private @Nullable Drag drag;

    /** What the gizmo moves, or null for none. Changing it cancels a drag. */
    public @Nullable GizmoTarget target() {
        return this.target;
    }

    public void setTarget(@Nullable GizmoTarget target) {
        if (target == this.target) return;

        this.cancel();
        this.target = target;
        this.hovered = null;
    }

    public GizmoOperation operation() {
        return this.operation;
    }

    /** Changes what dragging does; cancels a drag. */
    public void setOperation(GizmoOperation operation) {
        if (operation == this.operation) return;

        this.cancel();
        this.operation = operation;
        this.hovered = null;
    }

    public GizmoSpace space() {
        return this.space;
    }

    /** Changes the axes translation and rotation follow; cancels a drag. */
    public void setSpace(GizmoSpace space) {
        if (space == this.space) return;

        this.cancel();
        this.space = space;
    }

    public boolean isSnapping() {
        return this.snapping;
    }

    public void setSnapping(boolean snapping) {
        this.snapping = snapping;
    }

    public double translationStep() {
        return this.translationStep;
    }

    /** In degrees. */
    public double rotationStep() {
        return this.rotationStep;
    }

    public double scaleStep() {
        return this.scaleStep;
    }

    /**
     * The steps snapping rounds to: a translation in the view's units, a rotation in degrees, and a scale.
     */
    public void setSteps(double translation, double degrees, double scale) {
        if (!(translation > 0.0) || !(degrees > 0.0) || !(scale > 0.0)) {
            throw new IllegalArgumentException("Snap steps must be positive");
        }
        this.translationStep = translation;
        this.rotationStep = degrees;
        this.scaleStep = scale;
    }

    /** The length of an axis on screen, in GUI pixels. */
    public int size() {
        return this.size;
    }

    public void setSize(int pixels) {
        if (pixels <= 0) throw new IllegalArgumentException("A gizmo's size must be positive");

        this.size = pixels;
    }

    /** The handle under the pointer, or the one dragged; null for none. */
    public @Nullable GizmoHandle hovered() {
        return this.drag != null ? this.drag.handle : this.hovered;
    }

    public boolean isHovered() {
        return this.hovered() != null;
    }

    public boolean isDragging() {
        return this.drag != null;
    }

    /** Finds the handle under the pointer, in GUI pixels; returns whether there is one. */
    public boolean hover(GizmoView view, double x, double y) {
        if (this.drag != null) return true;

        this.hovered = this.pick(view, x, y);
        return this.hovered != null;
    }

    /** Forgets the hovered handle, as when the pointer leaves the view. */
    public void clearHover() {
        this.hovered = null;
    }

    /** Grabs the handle under the pointer; returns whether one was grabbed, so the click is taken. */
    public boolean press(GizmoView view, double x, double y) {
        if (this.drag != null || !this.hover(view, x, y) || this.target == null) return false;

        this.drag = this.start(view, this.target.pose(), this.hovered, x, y);
        return this.drag != null;
    }

    /**
     * Moves the grabbed handle to the pointer and previews the target there.
     *
     * @param invertSnap whether to snap when snapping is off and not to when it is on, as a held key does
     */
    public void drag(GizmoView view, double x, double y, boolean invertSnap) {
        Drag current = this.drag;
        if (current == null || this.target == null) return;

        GizmoPose pose = this.follow(current, view, x, y, this.snapping != invertSnap);
        if (pose == null || pose.equals(current.pose)) return;

        current.pose = pose;
        this.target.preview(pose);
    }

    /** Lets go of the handle and commits the drag, when it changed the pose. */
    public void release() {
        Drag current = this.drag;
        if (current == null) return;

        this.drag = null;
        if (this.target != null && !current.pose.equals(current.before)) this.target.commit(current.before, current.pose);
    }

    /** Lets go of the handle and puts the target back where the drag began. */
    public void cancel() {
        Drag current = this.drag;
        if (current == null) return;

        this.drag = null;
        if (this.target != null && !current.pose.equals(current.before)) this.target.preview(current.before);
    }

    /**
     * Where the handle can be grabbed on screen, in GUI pixels, or null when it is not drawn: another
     * operation's, seen edge-on, or behind the camera. For scripted input and tests.
     */
    public @Nullable Vector2f grabPoint(GizmoView view, GizmoHandle handle) {
        Frame frame = this.frame(view);
        if (frame == null || !this.handles().contains(handle) || !frame.shows(handle, this.operation)) return null;

        Vector3f point = switch (handle) {
            case CENTER -> new Vector3f(frame.center);
            case VIEW -> frame.along(GizmoMesh.perpendicular(frame.toCamera), VIEW_RING_RADIUS);
            case XY, YZ, XZ -> {
                int[] plane = plane(handle);
                yield frame.along(new Vector3f(frame.axes[plane[0]]).add(frame.axes[plane[1]]),
                        (PLANE_NEAR + PLANE_FAR) * 0.5F);
            }
            case X, Y, Z -> switch (this.operation) {
                case TRANSLATE -> frame.along(frame.axes[handle.axis()], (SHAFT_START + SHAFT_END) * 0.5F);
                case SCALE -> frame.along(frame.axes[handle.axis()], SCALE_CUBE);
                case ROTATE -> {
                    Vector3f across = new Vector3f(frame.axes[handle.axis()]).cross(frame.toCamera);
                    if (across.lengthSquared() < 1.0E-6F) across.set(frame.axes[(handle.axis() + 1) % 3]);
                    yield frame.along(across.normalize(), RING_RADIUS);
                }
            };
        };
        Vector3f screen = view.project(point);
        return screen == null ? null : new Vector2f(screen.x, screen.y);
    }

    /**
     * Draws the gizmo into a screen, over what was drawn before and under what is drawn after. For a
     * gizmo in the world, use {@link WorldGizmos#show}.
     */
    public void render(GuiGraphics graphics, GizmoView view) {
        GizmoMesh mesh = this.mesh(view);
        if (mesh == null || mesh.size() == 0) return;

        graphics.nextStratum();
        graphics.guiRenderState.submitGuiElement(new GizmoGuiRenderState(new Matrix3x2f(graphics.pose()), mesh, view));
        graphics.nextStratum();
    }

    /** The gizmo's triangles as seen through the view, or null when there is nothing to draw. */
    @Nullable GizmoMesh mesh(GizmoView view) {
        Frame frame = this.frame(view);
        if (frame == null) return null;

        GizmoHandle active = this.hovered();
        GizmoMesh mesh = new GizmoMesh(view);
        for (GizmoHandle handle : this.handles()) {
            if (this.drag != null && handle != this.drag.handle) continue;
            if (!frame.shows(handle, this.operation)) continue;

            this.build(mesh, frame, handle, handle == active);
        }
        return mesh;
    }

    private void build(GizmoMesh mesh, Frame frame, GizmoHandle handle, boolean active) {
        float scale = frame.scale;
        Vector3f center = frame.center;
        switch (handle) {
            case CENTER -> mesh.cube(center, frame.axes, CENTER_HALF * scale, active ? ACTIVE_COLOR : CENTER_COLOR);
            case VIEW -> {
                Vector3f u = GizmoMesh.perpendicular(frame.toCamera);
                Vector3f v = new Vector3f(frame.toCamera).cross(u);
                mesh.ring(center, u, v, VIEW_RING_RADIUS * scale, RING_THICKNESS * 0.75F * scale, RING_SEGMENTS,
                        active ? ACTIVE_COLOR : VIEW_RING_COLOR);
            }
            case XY, YZ, XZ -> {
                int[] plane = plane(handle);
                Vector3f u = frame.axes[plane[0]];
                Vector3f v = frame.axes[plane[1]];
                int color = (active ? ACTIVE_COLOR : AXIS_COLORS[handle.axis()]) & 0x00FFFFFF | PLANE_ALPHA << 24;
                mesh.flat(corner(center, u, v, PLANE_NEAR, PLANE_NEAR, scale), corner(center, u, v, PLANE_FAR, PLANE_NEAR, scale),
                        corner(center, u, v, PLANE_FAR, PLANE_FAR, scale), color);
                mesh.flat(corner(center, u, v, PLANE_NEAR, PLANE_NEAR, scale), corner(center, u, v, PLANE_FAR, PLANE_FAR, scale),
                        corner(center, u, v, PLANE_NEAR, PLANE_FAR, scale), color);
            }
            case X, Y, Z -> {
                int axis = handle.axis();
                Vector3f direction = frame.axes[axis];
                int color = active ? ACTIVE_COLOR : AXIS_COLORS[axis];
                switch (this.operation) {
                    case TRANSLATE -> {
                        mesh.tube(frame.along(direction, SHAFT_START), frame.along(direction, SHAFT_END),
                                SHAFT_RADIUS * scale, 8, color);
                        mesh.cone(frame.along(direction, SHAFT_END), frame.along(direction, 1.0F), CONE_RADIUS * scale,
                                12, color);
                    }
                    case SCALE -> {
                        mesh.tube(frame.along(direction, SHAFT_START), frame.along(direction, SCALE_CUBE - SCALE_CUBE_HALF),
                                SHAFT_RADIUS * scale, 8, color);
                        mesh.cube(frame.along(direction, SCALE_CUBE), frame.axes, SCALE_CUBE_HALF * scale, color);
                    }
                    case ROTATE -> mesh.ring(center, frame.axes[(axis + 1) % 3], frame.axes[(axis + 2) % 3],
                            RING_RADIUS * scale, RING_THICKNESS * scale, RING_SEGMENTS, color);
                }
            }
        }
    }

    private @Nullable GizmoHandle pick(GizmoView view, double x, double y) {
        Frame frame = this.frame(view);
        if (frame == null) return null;

        Vector3f center = view.project(frame.center);
        if (center == null) return null;

        GizmoHandle best = null;
        float bestDistance = PICK_PIXELS;
        for (GizmoHandle handle : this.handles()) {
            if (!frame.shows(handle, this.operation)) continue;

            switch (handle) {
                case CENTER -> {
                    if (Math.hypot(x - center.x, y - center.y) <= CENTER_HALF * this.size + 4.0F) return handle;
                }
                case XY, YZ, XZ -> {
                    if (this.insidePlane(view, frame, handle, x, y)) return handle;
                }
                case VIEW -> {
                    Vector3f u = GizmoMesh.perpendicular(frame.toCamera);
                    float distance = ringDistance(view, frame.center, u, new Vector3f(frame.toCamera).cross(u),
                            VIEW_RING_RADIUS * frame.scale, x, y);
                    if (distance < bestDistance) {
                        best = handle;
                        bestDistance = distance;
                    }
                }
                case X, Y, Z -> {
                    float distance = this.axisDistance(view, frame, handle.axis(), x, y);
                    if (distance < bestDistance) {
                        best = handle;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    private float axisDistance(GizmoView view, Frame frame, int axis, double x, double y) {
        Vector3f direction = frame.axes[axis];
        if (this.operation == GizmoOperation.ROTATE) {
            float facing = Math.abs(direction.dot(frame.toCamera));
            return ringDistance(view, frame.center, frame.axes[(axis + 1) % 3], frame.axes[(axis + 2) % 3],
                    RING_RADIUS * frame.scale, x, y) + (1.0F - facing) * EDGE_ON_BIAS;
        }
        float end = this.operation == GizmoOperation.SCALE ? SCALE_CUBE + SCALE_CUBE_HALF : 1.0F;
        return segmentDistance(view.project(frame.along(direction, SHAFT_START)), view.project(frame.along(direction, end)),
                x, y);
    }

    private boolean insidePlane(GizmoView view, Frame frame, GizmoHandle handle, double x, double y) {
        int[] plane = plane(handle);
        Vector3f u = frame.axes[plane[0]];
        Vector3f v = frame.axes[plane[1]];
        Vector3f[] corners = {
                view.project(corner(frame.center, u, v, PLANE_NEAR, PLANE_NEAR, frame.scale)),
                view.project(corner(frame.center, u, v, PLANE_FAR, PLANE_NEAR, frame.scale)),
                view.project(corner(frame.center, u, v, PLANE_FAR, PLANE_FAR, frame.scale)),
                view.project(corner(frame.center, u, v, PLANE_NEAR, PLANE_FAR, frame.scale)),
        };
        int sign = 0;
        for (int i = 0; i < 4; i++) {
            Vector3f a = corners[i];
            Vector3f b = corners[(i + 1) % 4];
            if (a == null || b == null) return false;

            double cross = (b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x);
            int side = cross > 0.0 ? 1 : -1;
            if (sign != 0 && side != sign) return false;

            sign = side;
        }
        return true;
    }

    private @Nullable Drag start(GizmoView view, GizmoPose before, GizmoHandle handle, double x, double y) {
        Frame frame = this.frame(view);
        if (frame == null) return null;

        Drag started = new Drag(handle, before, frame.axes, x, y);
        Vector3f origin = new Vector3f();
        Vector3f direction = new Vector3f();
        view.ray(x, y, origin, direction);
        switch (this.operation) {
            case TRANSLATE -> {
                if (handle.isAxis()) {
                    started.startParameter = orZero(closestParameter(frame.center, frame.axes[handle.axis()], origin, direction));
                } else {
                    started.normal.set(handle.isPlane() ? frame.axes[handle.axis()] : view.forward());
                    Vector3f hit = intersect(origin, direction, frame.center, started.normal);
                    if (hit == null) return null;

                    started.offset.set(hit).sub(frame.center);
                }
            }
            case ROTATE -> {
                Vector3f center = view.project(frame.center);
                if (center == null) return null;

                started.normal.set(handle == GizmoHandle.VIEW ? frame.toCamera : frame.axes[handle.axis()]);
                started.sign = started.normal.dot(frame.toCamera) >= 0.0F ? 1.0F : -1.0F;
                started.lastAngle = angle(center, x, y);
            }
            case SCALE -> {
                if (handle.isAxis()) {
                    float parameter = orZero(closestParameter(frame.center, frame.axes[handle.axis()], origin, direction));
                    started.startParameter = Math.abs(parameter) < 1.0E-3F * frame.scale ? SCALE_CUBE * frame.scale : parameter;
                }
            }
        }
        return started;
    }

    private @Nullable GizmoPose follow(Drag drag, GizmoView view, double x, double y, boolean snap) {
        Vector3f center = view.relative(drag.before.position());
        Vector3f origin = new Vector3f();
        Vector3f direction = new Vector3f();
        view.ray(x, y, origin, direction);
        return switch (this.operation) {
            case TRANSLATE -> this.translate(drag, center, origin, direction, snap);
            case ROTATE -> {
                Vector3f projected = view.project(center);
                if (projected == null) yield null;

                float angle = angle(projected, x, y);
                float turn = angle - drag.lastAngle;
                if (turn > Math.PI) turn -= (float) (2.0 * Math.PI);
                if (turn < -Math.PI) turn += (float) (2.0 * Math.PI);
                drag.angle += turn;
                drag.lastAngle = angle;
                float radians = drag.sign * drag.angle;
                if (snap) radians = (float) Math.toRadians(round(Math.toDegrees(radians), this.rotationStep));
                yield drag.before.withRotation(new Quaternionf().rotationAxis(radians, drag.normal)
                        .mul(drag.before.rotation()).normalize());
            }
            case SCALE -> {
                Vector3f scale = drag.before.scale();
                if (drag.handle.isAxis()) {
                    float parameter = closestParameter(center, drag.axes[drag.handle.axis()], origin, direction);
                    if (Float.isNaN(parameter)) yield null;

                    int axis = drag.handle.axis();
                    scale.setComponent(axis, this.scaled(scale.get(axis) * parameter / drag.startParameter, snap));
                } else {
                    float factor = (float) (1.0 + ((x - drag.startX) - (y - drag.startY)) / this.size);
                    for (int axis = 0; axis < 3; axis++) {
                        scale.setComponent(axis, this.scaled(scale.get(axis) * factor, snap));
                    }
                }
                yield drag.before.withScale(scale);
            }
        };
    }

    private @Nullable GizmoPose translate(Drag drag, Vector3f center, Vector3f origin, Vector3f direction, boolean snap) {
        Vector3f moved = new Vector3f();
        GizmoHandle handle = drag.handle;
        if (handle.isAxis()) {
            float parameter = closestParameter(center, drag.axes[handle.axis()], origin, direction);
            if (Float.isNaN(parameter)) return null;

            float distance = parameter - drag.startParameter;
            if (snap) distance = (float) round(distance, this.translationStep);
            moved.set(drag.axes[handle.axis()]).mul(distance);
        } else {
            Vector3f hit = intersect(origin, direction, center, drag.normal);
            if (hit == null) return null;

            moved.set(hit).sub(center).sub(drag.offset);
            if (snap && handle.isPlane()) {
                int[] plane = plane(handle);
                Vector3f u = drag.axes[plane[0]];
                Vector3f v = drag.axes[plane[1]];
                float along = (float) round(moved.dot(u), this.translationStep);
                float across = (float) round(moved.dot(v), this.translationStep);
                moved.set(u).mul(along).add(new Vector3f(v).mul(across));
            } else if (snap) {
                moved.set(round(moved.x, this.translationStep), round(moved.y, this.translationStep),
                        round(moved.z, this.translationStep));
            }
        }
        return drag.before.withPosition(drag.before.position().add(new Vector3d(moved)));
    }

    private float scaled(float value, boolean snap) {
        float result = snap ? (float) round(value, this.scaleStep) : value;
        float minimum = snap ? (float) Math.max(MIN_SCALE, this.scaleStep) : MIN_SCALE;
        return Math.max(result, minimum);
    }

    private List<GizmoHandle> handles() {
        return switch (this.operation) {
            case TRANSLATE -> TRANSLATE_HANDLES;
            case ROTATE -> ROTATE_HANDLES;
            case SCALE -> SCALE_HANDLES;
        };
    }

    /** The gizmo's placement through the view, or null when nothing is drawn: no target, or behind the camera. */
    private @Nullable Frame frame(GizmoView view) {
        GizmoTarget current = this.target;
        if (current == null || !current.supports(this.operation)) return null;

        GizmoPose pose = current.pose();
        Vector3f center = view.relative(pose.position());
        float depth = view.depth(center);
        if (depth <= 0.05F) return null;

        Quaternionf rotation = this.space == GizmoSpace.LOCAL || this.operation == GizmoOperation.SCALE
                ? pose.rotation() : new Quaternionf();
        Vector3f[] axes = {
                rotation.transform(new Vector3f(1.0F, 0.0F, 0.0F)),
                rotation.transform(new Vector3f(0.0F, 1.0F, 0.0F)),
                rotation.transform(new Vector3f(0.0F, 0.0F, 1.0F)),
        };
        return new Frame(center, axes, view.pixelSize(depth) * this.size, view.toEye(center));
    }

    private static int[] plane(GizmoHandle handle) {
        return switch (handle) {
            case XY -> new int[] {0, 1};
            case YZ -> new int[] {1, 2};
            case XZ -> new int[] {0, 2};
            default -> throw new IllegalArgumentException(handle + " is not a plane");
        };
    }

    private static Vector3f corner(Vector3fc center, Vector3fc u, Vector3fc v, float along, float across, float scale) {
        return new Vector3f(u).mul(along * scale).add(new Vector3f(v).mul(across * scale)).add(center);
    }

    /** The pointer's angle around a point on screen, counterclockwise as the viewer sees it. */
    private static float angle(Vector3f center, double x, double y) {
        return (float) Math.atan2(center.y - y, x - center.x);
    }

    private static float ringDistance(GizmoView view, Vector3fc center, Vector3fc u, Vector3fc v, float radius,
                                      double x, double y) {
        float best = Float.MAX_VALUE;
        Vector3f previous = null;
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = 2.0 * Math.PI * i / RING_SEGMENTS;
            Vector3f point = view.project(new Vector3f(u).mul((float) Math.cos(angle) * radius)
                    .add(new Vector3f(v).mul((float) Math.sin(angle) * radius)).add(center));
            if (previous != null && point != null) best = Math.min(best, segmentDistance(previous, point, x, y));
            previous = point;
        }
        return best;
    }

    private static float segmentDistance(@Nullable Vector3f a, @Nullable Vector3f b, double x, double y) {
        if (a == null || b == null) return Float.MAX_VALUE;

        double dx = b.x - a.x;
        double dy = b.y - a.y;
        double lengthSquared = dx * dx + dy * dy;
        double t = lengthSquared < 1.0E-9 ? 0.0 : Math.clamp(((x - a.x) * dx + (y - a.y) * dy) / lengthSquared, 0.0, 1.0);
        return (float) Math.hypot(x - (a.x + t * dx), y - (a.y + t * dy));
    }

    /**
     * How far along a line through {@code point} with unit {@code axis} the ray passes nearest, or NaN when
     * they are parallel.
     */
    static float closestParameter(Vector3fc point, Vector3fc axis, Vector3fc origin, Vector3fc direction) {
        Vector3f offset = new Vector3f(point).sub(origin);
        float cosine = axis.dot(direction);
        float denominator = 1.0F - cosine * cosine;
        if (denominator < 1.0E-6F) return Float.NaN;

        return (cosine * direction.dot(offset) - axis.dot(offset)) / denominator;
    }

    private static @Nullable Vector3f intersect(Vector3fc origin, Vector3fc direction, Vector3fc point, Vector3fc normal) {
        float facing = normal.dot(direction);
        if (Math.abs(facing) < 1.0E-6F) return null;

        float distance = new Vector3f(point).sub(origin).dot(normal) / facing;
        return distance < 0.0F ? null : new Vector3f(direction).mul(distance).add(origin);
    }

    private static double round(double value, double step) {
        return Math.round(value / step) * step;
    }

    private static float orZero(float value) {
        return Float.isNaN(value) ? 0.0F : value;
    }

    /** Where the gizmo stands through a view: its camera-relative centre, axes, size and the way to the camera. */
    private record Frame(Vector3f center, Vector3f[] axes, float scale, Vector3f toCamera) {

        Vector3f along(Vector3fc direction, float distance) {
            return new Vector3f(direction).mul(distance * this.scale).add(this.center);
        }

        /** Whether a handle is drawn: not an axis seen end-on, nor a plane seen edge-on. */
        boolean shows(GizmoHandle handle, GizmoOperation operation) {
            if (handle.isAxis() && operation != GizmoOperation.ROTATE) {
                return Math.abs(this.axes[handle.axis()].dot(this.toCamera)) < 0.985F;
            }
            if (handle.isPlane()) return Math.abs(this.axes[handle.axis()].dot(this.toCamera)) > 0.2F;
            return true;
        }
    }

    /** A drag under way: the pose it began from and the latest, and what the grab fixed. */
    private static final class Drag {

        private final GizmoHandle handle;
        private final GizmoPose before;
        private final Vector3f[] axes;
        private final double startX;
        private final double startY;
        private final Vector3f normal = new Vector3f();
        private final Vector3f offset = new Vector3f();
        private GizmoPose pose;
        private float startParameter;
        private float sign = 1.0F;
        private float lastAngle;
        private float angle;

        private Drag(GizmoHandle handle, GizmoPose before, Vector3f[] axes, double startX, double startY) {
            this.handle = handle;
            this.before = before;
            this.pose = before;
            this.axes = axes;
            this.startX = startX;
            this.startY = startY;
        }
    }
}
