package fr.lacaleche.glue.client.render.gizmo;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.Comparator;

public class GizmoRenderer {

    private static final int DEFAULT_CONE_SEGMENTS = 32;
    private static final int MAX_FACES = 128;

    private static final float XF = 0.7f; // X-axis face brightness
    private static final float YPF = 1.0f; // Y+ face brightness (top faces - brightest)
    private static final float YNF = 0.6f; // Y- face brightness (bottom faces - darkest)
    private static final float ZF = 0.87f; // Z-axis face brightness

    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projMatrix = new Matrix4f();

    private final Vector2f projectionTemp = new Vector2f();
    private final RenderFace[] faceBuffer = new RenderFace[MAX_FACES];
    private float vx, vy, vw, vh;

    private boolean enableBackfaceCulling = false;

    /**
     * Constructs a new GizmoRenderer with default settings.
     */
    public GizmoRenderer() {
        for (int i = 0; i < faceBuffer.length; i++) {
            faceBuffer[i] = new RenderFace();
        }
    }

    /**
     * Updates the camera context for 3D-to-2D projection.
     *
     * @param view 4x4 view matrix (float[16] in column-major order)
     * @param proj 4x4 projection matrix (float[16] in column-major order)
     * @param x    Viewport X offset (screen space)
     * @param y    Viewport Y offset (screen space)
     * @param w    Viewport width (screen space)
     * @param h    Viewport height (screen space)
     */
    public void updateContext(float[] view, float[] proj, float x, float y, float w, float h) {
        this.viewMatrix.set(view);
        this.projMatrix.set(proj);
        this.vx = x;
        this.vy = y;
        this.vw = w;
        this.vh = h;
    }

    /**
     * Enables or disables backface culling for rendered faces.
     * When enabled, faces facing away from the camera are not rendered.
     *
     * @param enable true to enable backface culling
     */
    public void setBackfaceCulling(boolean enable) {
        this.enableBackfaceCulling = enable;
    }

    /**
     * Draws a 3D line in screen space.
     * Silently culls if either endpoint is behind the camera or outside the
     * frustum.
     *
     * @param backend   Rendering backend
     * @param start     Line start position (world space)
     * @param end       Line end position (world space)
     * @param color     ARGB color (0xAARRGGBB)
     * @param thickness Line thickness in pixels
     */
    public void drawLine(GizmoRenderBackend backend, Vector3f start, Vector3f end, int color, float thickness) {
        if (!GizmoMath.project(start, viewMatrix, projMatrix, vx, vy, vw, vh, projectionTemp))
            return;
        float x1 = projectionTemp.x;
        float y1 = projectionTemp.y;

        if (!GizmoMath.project(end, viewMatrix, projMatrix, vx, vy, vw, vh, projectionTemp))
            return;
        float x2 = projectionTemp.x;
        float y2 = projectionTemp.y;

        backend.drawLine(x1, y1, x2, y2, color, thickness);
    }

    /**
     * Draws a shaded 3D oriented cube centered at a point.
     * Defined by three orthonormal basis vectors.
     *
     * @param backend Rendering backend
     * @param center  Cube center position (world space)
     * @param xDir    X-axis basis vector (normalized)
     * @param yDir    Y-axis basis vector (normalized)
     * @param zDir    Z-axis basis vector (normalized)
     * @param size    Cube side length
     * @param color   ARGB color before lighting (0xAARRGGBB)
     */
    public void drawOrientedCube(GizmoRenderBackend backend, Vector3f center, Vector3f xDir, Vector3f yDir, Vector3f zDir,
                                 float size, int color) {
        float hs = size * 0.5f;

        Vector3f dx = new Vector3f(xDir).mul(hs);
        Vector3f dy = new Vector3f(yDir).mul(hs);
        Vector3f dz = new Vector3f(zDir).mul(hs);

        // 8 Corners relative to center
        Vector3f[] p = new Vector3f[8];
        p[0] = new Vector3f(center).sub(dx).sub(dy).sub(dz);
        p[1] = new Vector3f(center).add(dx).sub(dy).sub(dz);
        p[2] = new Vector3f(center).add(dx).add(dy).sub(dz);
        p[3] = new Vector3f(center).sub(dx).add(dy).sub(dz);
        p[4] = new Vector3f(center).sub(dx).sub(dy).add(dz);
        p[5] = new Vector3f(center).add(dx).sub(dy).add(dz);
        p[6] = new Vector3f(center).add(dx).add(dy).add(dz);
        p[7] = new Vector3f(center).sub(dx).add(dy).add(dz);

        int count = 0;
        addQuad(count++, p[0], p[1], p[2], p[3], new Vector3f(zDir).negate(), color);
        addQuad(count++, p[4], p[5], p[6], p[7], new Vector3f(zDir), color);
        addQuad(count++, p[0], p[1], p[5], p[4], new Vector3f(yDir).negate(), color);
        addQuad(count++, p[3], p[2], p[6], p[7], new Vector3f(yDir), color);
        addQuad(count++, p[0], p[4], p[7], p[3], new Vector3f(xDir).negate(), color);
        addQuad(count++, p[1], p[5], p[6], p[2], new Vector3f(xDir), color);

        renderFaces(backend, count);
    }

    /**
     * Draws a shaded 3D axis-aligned cube centered at a point.
     * Uses directional lighting for depth perception.
     *
     * @param backend   Rendering backend
     * @param center    Cube center position (world space)
     * @param size      Cube side length
     * @param baseColor ARGB color before lighting (0xAARRGGBB)
     */
    public void drawCube(GizmoRenderBackend backend, Vector3f center, float size, int baseColor) {
        float hs = size * 0.5f;

        Vector3f[] p = new Vector3f[8];
        p[0] = new Vector3f(center.x - hs, center.y - hs, center.z - hs);
        p[1] = new Vector3f(center.x + hs, center.y - hs, center.z - hs);
        p[2] = new Vector3f(center.x + hs, center.y + hs, center.z - hs);
        p[3] = new Vector3f(center.x - hs, center.y + hs, center.z - hs);
        p[4] = new Vector3f(center.x - hs, center.y - hs, center.z + hs);
        p[5] = new Vector3f(center.x + hs, center.y - hs, center.z + hs);
        p[6] = new Vector3f(center.x + hs, center.y + hs, center.z + hs);
        p[7] = new Vector3f(center.x - hs, center.y + hs, center.z + hs);

        int count = 0;
        addQuad(count++, p[0], p[1], p[2], p[3], new Vector3f(0, 0, -1), baseColor); // Back (-Z)
        addQuad(count++, p[4], p[5], p[6], p[7], new Vector3f(0, 0, 1), baseColor); // Front (+Z)
        addQuad(count++, p[0], p[1], p[5], p[4], new Vector3f(0, -1, 0), baseColor); // Bottom (-Y)
        addQuad(count++, p[3], p[2], p[6], p[7], new Vector3f(0, 1, 0), baseColor); // Top (+Y)
        addQuad(count++, p[0], p[4], p[7], p[3], new Vector3f(-1, 0, 0), baseColor); // Left (-X)
        addQuad(count++, p[1], p[5], p[6], p[2], new Vector3f(1, 0, 0), baseColor); // Right (+X)

        renderFaces(backend, count);
    }

    /**
     * Draws a high-fidelity shaded 3D cone (typically used for arrow heads).
     * Generates 32 triangular segments for smooth curvature.
     *
     * @param backend    Rendering backend
     * @param baseCenter Cone base center position (world space)
     * @param length     Cone height (distance from base to tip)
     * @param radius     Cone base radius
     * @param direction  Cone axis direction (must be normalized)
     * @param baseColor  ARGB color before lighting (0xAARRGGBB)
     */
    public void drawCone(GizmoRenderBackend backend, Vector3f baseCenter, float length, float radius, Vector3f direction,
                         int baseColor) {
        // Construct orthonormal basis for cone
        Vector3f up = new Vector3f(0, 1, 0);
        if (Math.abs(direction.y) > 0.99f)
            up.set(1, 0, 0); // Avoid parallel vectors
        Vector3f right = new Vector3f(direction).cross(up).normalize();
        up.set(right).cross(direction).normalize();

        right.mul(radius);
        up.mul(radius);

        Vector3f tip = new Vector3f(direction).mul(length).add(baseCenter);
        int segments = DEFAULT_CONE_SEGMENTS;
        int count = 0;

        Vector3f[] basePoints = new Vector3f[segments];

        for (int i = 0; i < segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            float ca = (float) Math.cos(angle);
            float sa = (float) Math.sin(angle);
            basePoints[i] = new Vector3f(baseCenter).add(
                    right.x * ca + up.x * sa,
                    right.y * ca + up.y * sa,
                    right.z * ca + up.z * sa);
        }

        Vector3f baseNormal = new Vector3f(direction).negate();
        for (int i = 0; i < segments; i++) {
            if (count >= faceBuffer.length)
                break;
            int next = (i + 1) % segments;
            addTriangle(count++, baseCenter, basePoints[next], basePoints[i], baseNormal, baseColor);
        }

        for (int i = 0; i < segments; i++) {
            if (count >= faceBuffer.length)
                break;
            int next = (i + 1) % segments;

            Vector3f midBase = new Vector3f(basePoints[i]).add(basePoints[next]).mul(0.5f);
            Vector3f toTip = new Vector3f(tip).sub(midBase);
            Vector3f tangent = new Vector3f(basePoints[next]).sub(basePoints[i]);
            Vector3f sideNormal = new Vector3f(toTip).cross(tangent).normalize();

            addTriangle(count++, basePoints[i], basePoints[next], tip, sideNormal, baseColor);
        }

        renderFaces(backend, count);
    }

    /**
     * Draws a 3D circle (wireframe) oriented by a normal vector.
     * Useful for rotation gizmos and orientation indicators.
     *
     * @param backend  Rendering backend
     * @param center   Circle center position (world space)
     * @param radius   Circle radius
     * @param normal   Circle plane normal (must be normalized)
     * @param color    ARGB color (0xAARRGGBB)
     * @param segments Number of line segments (higher = smoother)
     */
    public void drawCircle(GizmoRenderBackend backend, Vector3f center, float radius, Vector3f normal, int color,
                           int segments) {
        Vector3f u = new Vector3f();
        Vector3f v = new Vector3f();

        if (Math.abs(normal.y) < 0.99f) {
            u.set(normal.z, 0, -normal.x).normalize();
        } else {
            u.set(1, 0, 0);
        }
        normal.cross(u, v).normalize();

        u.mul(radius);
        v.mul(radius);

        Vector2f lastScreen = new Vector2f();
        Vector2f currScreen = new Vector2f();
        boolean first = true;

        for (int i = 0; i <= segments; i++) {
            float angle = (float) (i * 2.0 * Math.PI / segments);
            Vector3f p = new Vector3f(center).add(
                    u.x * (float) Math.cos(angle) + v.x * (float) Math.sin(angle),
                    u.y * (float) Math.cos(angle) + v.y * (float) Math.sin(angle),
                    u.z * (float) Math.cos(angle) + v.z * (float) Math.sin(angle));

            if (!GizmoMath.project(p, viewMatrix, projMatrix, vx, vy, vw, vh, currScreen)) {
                first = true; // Break strip if point is clipped
                continue;
            }

            if (!first) {
                backend.drawLine(lastScreen.x, lastScreen.y, currScreen.x, currScreen.y, color, 3.0f);
            }
            lastScreen.set(currScreen);
            first = false;
        }
    }

    // ===== Internal Rendering Pipeline =====

    private void addQuad(int index, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, Vector3f normal, int color) {
        if (index >= faceBuffer.length)
            return;
        RenderFace f = faceBuffer[index];
        f.reset();
        f.normal.set(normal);
        f.addPoint(p0, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.addPoint(p1, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.addPoint(p2, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.addPoint(p3, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.color = applyShinyLighting(color, normal);
    }

    private void addTriangle(int index, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f normal, int color) {
        if (index >= faceBuffer.length)
            return;
        RenderFace f = faceBuffer[index];
        f.reset();
        f.normal.set(normal);
        f.addPoint(p0, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.addPoint(p1, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.addPoint(p2, viewMatrix, projMatrix, vx, vy, vw, vh);
        f.color = applyShinyLighting(color, normal);
    }

    private int applyShinyLighting(int color, Vector3f normal) {
        float nx2 = normal.x * normal.x;
        float ny2 = normal.y * normal.y;
        float nz2 = normal.z * normal.z;

        float shade = nx2 * XF;
        if (normal.y > 0.0f) {
            shade += ny2 * YPF;
        } else {
            shade += ny2 * YNF;
        }
        shade += nz2 * ZF;

        return scaleColorChannels(color, shade);
    }

    private int scaleColorChannels(int color, float scale) {
        int a = (color >>> 24) & 0xFF;
        int r = clamp((int) (((color >>> 16) & 0xFF) * scale), 0, 255);
        int g = clamp((int) (((color >>> 8) & 0xFF) * scale), 0, 255);
        int b = clamp((int) ((color & 0xFF) * scale), 0, 255);

        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void renderFaces(GizmoRenderBackend backend, int count) {
        Arrays.sort(faceBuffer, 0, count, Comparator.comparingDouble(RenderFace::getDepth));

        for (int i = 0; i < count; i++) {
            RenderFace f = faceBuffer[i];

            if (!f.valid)
                continue;

            if (enableBackfaceCulling && !f.isFrontFacing(viewMatrix))
                continue;

            if (f.count == 3) {
                backend.drawTriangle(f.pts[0], f.pts[1], f.pts[2], f.pts[3], f.pts[4], f.pts[5], f.color);
            } else if (f.count == 4) {
                backend.drawQuad(f.pts[0], f.pts[1], f.pts[2], f.pts[3], f.pts[4], f.pts[5], f.pts[6], f.pts[7],
                        f.color);
            }
        }
    }

    private static class RenderFace {
        float[] pts = new float[16];
        int count = 0;
        float depthSum = 0;
        int color;
        boolean valid = true;
        Vector3f normal = new Vector3f();

        void reset() {
            count = 0;
            depthSum = 0;
            valid = true;
        }

        void addPoint(Vector3f p, Matrix4f view, Matrix4f proj, float vx, float vy, float vw, float vh) {
            Vector4f clip = new Vector4f(p, 1.0f).mul(view);
            depthSum += clip.z;
            clip.mul(proj);
            if (Math.abs(clip.w) < 1e-6f) {
                valid = false;
                return;
            }
            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            pts[count * 2] = vx + (ndcX + 1.0f) * 0.5f * vw;
            pts[count * 2 + 1] = vy + (1.0f - ndcY) * 0.5f * vh;
            count++;
        }

        double getDepth() {
            return count > 0 ? depthSum / count : 0;
        }

        boolean isFrontFacing(Matrix4f view) {
            Vector3f cameraForward = new Vector3f(
                    -view.m20(),
                    -view.m21(),
                    -view.m22());
            return normal.dot(cameraForward) > 0;
        }
    }
}
