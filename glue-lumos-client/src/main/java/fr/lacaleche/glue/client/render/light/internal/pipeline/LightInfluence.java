package fr.lacaleche.glue.client.render.light.internal.pipeline;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.LightType;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4f;

/** Camera-relative sphere conservatively enclosing one light's contribution. */
record LightInfluence(double x, double y, double z, double radius) {

    static LightInfluence of(Light light, Vector3d camera) {
        double centerOffset = 0.0;
        double radius = light.range;
        if (light.type != LightType.POINT) {
            double cosine = Math.clamp(light.cosOuter, -1.0, 1.0);
            if (cosine > 0.0) {
                double sine = Math.sqrt(1.0 - cosine * cosine);
                if (cosine >= Math.sqrt(0.5)) {
                    centerOffset = light.range / (2.0 * cosine);
                    radius = centerOffset;
                } else {
                    centerOffset = light.range * cosine;
                    radius = light.range * sine;
                }
            }
        }
        return new LightInfluence(
                light.x + light.directionX * centerOffset - camera.x,
                light.y + light.directionY * centerOffset - camera.y,
                light.z + light.directionZ * centerOffset - camera.z,
                radius);
    }

    int[] screenBounds(Matrix4f viewProjection, int width, int height) {
        if (x * x + y * y + z * z <= radius * radius) {
            return new int[]{0, 0, width, height};
        }

        float minX = 1f;
        float minY = 1f;
        float maxX = -1f;
        float maxY = -1f;
        Vector4f clip = new Vector4f();
        for (int corner = 0; corner < 8; corner++) {
            clip.set((float) (x + ((corner & 1) == 0 ? -radius : radius)),
                    (float) (y + ((corner & 2) == 0 ? -radius : radius)),
                    (float) (z + ((corner & 4) == 0 ? -radius : radius)), 1f);
            viewProjection.transform(clip);
            if (clip.w <= 0f) return new int[]{0, 0, width, height};
            float inverseW = 1f / clip.w;
            minX = Math.min(minX, clip.x * inverseW);
            minY = Math.min(minY, clip.y * inverseW);
            maxX = Math.max(maxX, clip.x * inverseW);
            maxY = Math.max(maxY, clip.y * inverseW);
        }

        int x0 = Math.max(0, (int) Math.floor((minX * 0.5f + 0.5f) * width) - 2);
        int y0 = Math.max(0, (int) Math.floor((minY * 0.5f + 0.5f) * height) - 2);
        int x1 = Math.min(width, (int) Math.ceil((maxX * 0.5f + 0.5f) * width) + 2);
        int y1 = Math.min(height, (int) Math.ceil((maxY * 0.5f + 0.5f) * height) + 2);
        if (x1 <= x0 || y1 <= y0) return null;
        return new int[]{x0, y0, x1 - x0, y1 - y0};
    }
}
