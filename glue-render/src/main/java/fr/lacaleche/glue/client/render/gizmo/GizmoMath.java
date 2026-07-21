package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.math.MatrixUtil;
import org.apache.commons.lang3.tuple.Triple;
import org.joml.*;

import java.lang.Math;

public class GizmoMath {

    public static boolean project(Vector3f worldPos, Matrix4f viewMatrix, Matrix4f projMatrix,
                                  float viewportX, float viewportY, float viewportW, float viewportH,
                                  Vector2f outScreenPos) {
        Vector4f pos = new Vector4f(worldPos, 1.0f);
        pos.mul(viewMatrix);
        pos.mul(projMatrix);
        if (pos.w <= 0.0f) {
            return false;
        }
        pos.x /= pos.w;
        pos.y /= pos.w;
        pos.z /= pos.w;
        outScreenPos.x = viewportX + (pos.x * 0.5f + 0.5f) * viewportW;
        outScreenPos.y = viewportY + (1.0f - (pos.y * 0.5f + 0.5f)) * viewportH;
        return true;
    }

    public static void decomposeMatrix(Matrix4f matrix, Vector3f outTranslation, Quaternionf outLeftRotation,
                                       Vector3f outScale, Quaternionf outRightRotation) {
        float f = 1.0F / matrix.m33();
        matrix.getTranslation(outTranslation).mul(f);
        Matrix3f linear = new Matrix3f(matrix).scale(f);
        Triple<Quaternionf, Vector3f, Quaternionf> result = MatrixUtil.svdDecompose(linear);
        outLeftRotation.set(result.getLeft());
        outScale.set(result.getMiddle());
        outRightRotation.set(result.getRight());
        if (Math.abs(outScale.x) < 1e-6f) outScale.x = Math.copySign(1e-6f, outScale.x);
        if (Math.abs(outScale.y) < 1e-6f) outScale.y = Math.copySign(1e-6f, outScale.y);
        if (Math.abs(outScale.z) < 1e-6f) outScale.z = Math.copySign(1e-6f, outScale.z);
    }

    public static float distSquaredToLineSegment(Vector2f p, Vector2f v, Vector2f w) {
        float l2 = v.distanceSquared(w);
        if (l2 == 0) return p.distanceSquared(v);
        float t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2;
        t = Math.max(0, Math.min(1, t));
        float dx = p.x - (v.x + t * (w.x - v.x));
        float dy = p.y - (v.y + t * (w.y - v.y));
        return dx * dx + dy * dy;
    }

    public static boolean intersectRayPlane(Vector3f rayOrigin, Vector3f rayDir, Vector3f planeOrigin,
                                            Vector3f planeNormal, Vector3f outIntersection) {
        float denom = planeNormal.dot(rayDir);
        if (Math.abs(denom) < 1e-6f) {
            return false;
        }
        Vector3f p0l0 = new Vector3f(planeOrigin).sub(rayOrigin);
        float t = p0l0.dot(planeNormal) / denom;
        if (t >= 0) {
            outIntersection.set(rayDir).mul(t).add(rayOrigin);
            return true;
        }
        return false;
    }

    public static boolean intersectRaySphere(Vector3f rayOrigin, Vector3f rayDir, Vector3f sphereCenter, float radiusSq,
                                             Vector2f outInterval) {
        Vector3f m = new Vector3f(rayOrigin).sub(sphereCenter);
        float b = m.dot(rayDir);
        float c = m.dot(m) - radiusSq;
        if (c > 0.0f && b > 0.0f) return false;
        float discr = b * b - c;
        if (discr < 0.0f) return false;
        float sqrtDiscr = (float) Math.sqrt(discr);
        outInterval.x = -b - sqrtDiscr;
        outInterval.y = -b + sqrtDiscr;
        return true;
    }

    public static void getRayFromScreen(float screenX, float screenY, float vx, float vy, float vw, float vh,
                                        Matrix4f view, Matrix4f proj, Vector3f outOrigin, Vector3f outDir) {
        float ndcX = ((screenX - vx) / vw) * 2.0f - 1.0f;
        float ndcY = 1.0f - ((screenY - vy) / vh) * 2.0f;
        Matrix4f invViewProj = new Matrix4f(proj).mul(view).invert();
        Vector4f near = new Vector4f(ndcX, ndcY, -1.0f, 1.0f).mul(invViewProj);
        Vector4f far = new Vector4f(ndcX, ndcY, 1.0f, 1.0f).mul(invViewProj);
        near.div(near.w);
        far.div(far.w);
        outOrigin.set(near.x, near.y, near.z);
        outDir.set(far.x, far.y, far.z).sub(outOrigin).normalize();
    }

    public static boolean checkProjectedRing(float mouseX, float mouseY, Vector3f origin, Vector3f normal, float radius,
                                             float threshold, float vx, float vy, float vw, float vh, Matrix4f view, Matrix4f proj) {
        Vector3f rayOrg = new Vector3f();
        Vector3f rayDir = new Vector3f();
        getRayFromScreen(mouseX, mouseY, vx, vy, vw, vh, view, proj, rayOrg, rayDir);
        Vector3f intersect = new Vector3f();
        if (!intersectRayPlane(rayOrg, rayDir, origin, normal, intersect)) {
            return false;
        }
        float dist = intersect.distance(origin);
        return Math.abs(dist - radius) < threshold;
    }

    public static boolean checkProjectedAxis(Vector2f mouse, Vector2f pStart, Vector3f origin, Vector3f dir, float len,
                                             Matrix4f view, Matrix4f proj, float vx, float vy, float vw, float vh, float thresholdSq) {
        Vector3f endInfo = new Vector3f(dir).mul(len).add(origin);
        Vector2f pEnd = new Vector2f();
        if (!project(endInfo, view, proj, vx, vy, vw, vh, pEnd)) return false;
        return distSquaredToLineSegment(mouse, pStart, pEnd) < thresholdSq;
    }

    public static float intersectRayAABB(Vector3f origin, Vector3f dir, Vector3f boxMin, Vector3f boxMax) {
        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;
        float[] o = {origin.x, origin.y, origin.z};
        float[] d = {dir.x, dir.y, dir.z};
        float[] bMin = {boxMin.x, boxMin.y, boxMin.z};
        float[] bMax = {boxMax.x, boxMax.y, boxMax.z};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(d[i]) < 1e-8f) {
                if (o[i] < bMin[i] || o[i] > bMax[i]) return -1f;
            } else {
                float invD = 1.0f / d[i];
                float t1 = (bMin[i] - o[i]) * invD;
                float t2 = (bMax[i] - o[i]) * invD;
                if (t1 > t2) {
                    float tmp = t1;
                    t1 = t2;
                    t2 = tmp;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return -1f;
            }
        }
        if (tMin > 0) return tMin;
        if (tMax > 0) return tMax;
        return -1f;
    }
}
