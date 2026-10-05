package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.utils.FrameMatrices;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;

/**
 * The camera a {@link Gizmo} is seen and grabbed through, and the rectangle it is drawn in, in GUI pixels.
 * The view matrix is relative to the camera's position, as the level renders: a pose's position minus
 * {@link #camera} is what the matrix transforms. A scene with its own absolute view matrix passes a camera
 * at the origin.
 */
public final class GizmoView {

    private final Vector3d camera;
    private final Matrix4f viewProjection;
    private final Matrix4f inverse;
    private final Vector3f eye;
    private final Vector3f forward;
    private final Vector3f right;
    private final Vector3f up;
    private final float focal;
    private final float x;
    private final float y;
    private final float width;
    private final float height;

    /**
     * @param camera     the camera's position, which the view matrix is relative to
     * @param view       the camera-relative view matrix
     * @param projection a perspective projection
     * @param x          the drawn rectangle's left edge, in GUI pixels
     */
    public GizmoView(Vector3dc camera, Matrix4fc view, Matrix4fc projection, float x, float y, float width,
                     float height) {
        if (width <= 0.0F || height <= 0.0F) throw new IllegalArgumentException("A gizmo view needs an area");

        this.camera = new Vector3d(camera);
        this.viewProjection = new Matrix4f(projection).mul(view);
        this.inverse = new Matrix4f(this.viewProjection).invert();
        Matrix4f eye = new Matrix4f(view).invert();
        this.eye = eye.transformPosition(new Vector3f());
        this.forward = eye.transformDirection(new Vector3f(0.0F, 0.0F, -1.0F)).normalize();
        this.right = eye.transformDirection(new Vector3f(1.0F, 0.0F, 0.0F)).normalize();
        this.up = eye.transformDirection(new Vector3f(0.0F, 1.0F, 0.0F)).normalize();
        this.focal = projection.m11();
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    /**
     * The world as the level was last rendered, from {@link FrameMatrices}, over the whole window in GUI
     * pixels; null before the first frame.
     */
    public static @Nullable GizmoView world() {
        Matrix4f view = FrameMatrices.getView();
        Matrix4f projection = FrameMatrices.getProjection();
        if (view == null || projection == null) return null;

        Minecraft client = Minecraft.getInstance();
        Vec3 camera = client.gameRenderer.getMainCamera().getPosition();
        Window window = client.getWindow();
        return new GizmoView(new Vector3d(camera.x, camera.y, camera.z), view, projection, 0.0F, 0.0F,
                window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    public Vector3d camera() {
        return new Vector3d(this.camera);
    }

    /** A position relative to the camera, as the view matrix takes it. */
    Vector3f relative(Vector3dc position) {
        return new Vector3f((float) (position.x() - this.camera.x), (float) (position.y() - this.camera.y),
                (float) (position.z() - this.camera.z));
    }

    /**
     * Where a camera-relative point is drawn, in GUI pixels, or null behind the camera. The third component
     * is its distance along the view.
     */
    @Nullable Vector3f project(Vector3fc point) {
        Vector4f clip = new Vector4f(point.x(), point.y(), point.z(), 1.0F).mul(this.viewProjection);
        if (clip.w <= 1.0E-4F) return null;

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        return new Vector3f(this.x + (ndcX * 0.5F + 0.5F) * this.width,
                this.y + (0.5F - ndcY * 0.5F) * this.height, this.depth(point));
    }

    /** The ray under a point in GUI pixels: its camera-relative origin and its unit direction. */
    void ray(double pointerX, double pointerY, Vector3f origin, Vector3f direction) {
        float ndcX = (float) ((pointerX - this.x) / this.width * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (pointerY - this.y) / this.height * 2.0);
        Vector4f near = new Vector4f(ndcX, ndcY, -1.0F, 1.0F).mul(this.inverse);
        Vector4f far = new Vector4f(ndcX, ndcY, 1.0F, 1.0F).mul(this.inverse);
        origin.set(near.x / near.w, near.y / near.w, near.z / near.w);
        direction.set(far.x / far.w, far.y / far.w, far.z / far.w).sub(origin).normalize();
    }

    /** How far a camera-relative point lies along the view. */
    float depth(Vector3fc point) {
        return new Vector3f(point).sub(this.eye).dot(this.forward);
    }

    /**
     * The unit direction from a camera-relative point to the eye, which sits at the origin only when the
     * view matrix leaves the camera's position out.
     */
    Vector3f toEye(Vector3fc point) {
        Vector3f direction = new Vector3f(this.eye).sub(point);
        return direction.lengthSquared() < 1.0E-8F ? new Vector3f(this.forward).negate() : direction.normalize();
    }

    /** The length one GUI pixel covers at a distance along the view. */
    float pixelSize(float depth) {
        return 2.0F * Math.max(depth, 1.0E-3F) / (this.focal * this.height);
    }

    /** The unit direction the camera looks along. */
    Vector3fc forward() {
        return this.forward;
    }

    Vector3fc right() {
        return this.right;
    }

    Vector3fc up() {
        return this.up;
    }

    float left() {
        return this.x;
    }

    float top() {
        return this.y;
    }

    float width() {
        return this.width;
    }

    float height() {
        return this.height;
    }
}
