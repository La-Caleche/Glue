package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.lacaleche.glue.client.debug.GlueDebugRenderer;
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightManager;
import fr.lacaleche.glue.client.render.light.LightType;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import org.joml.Vector3f;

import java.util.List;

/**
 * In-world wireframe preview of every active {@link Light}, driven by
 * {@link LightDebugHud} (only draws while the HUD is open and its
 * "Shape preview" row is ON).
 *
 * <p>Point lights draw as three great circles at {@code range} (the reach
 * sphere); spots and gobos as their cone &mdash; apex lines of length exactly
 * {@code range} to the outer cap (so the wire is the true reach boundary, not
 * the axial distance), plus a dimmer inner-angle circle where full brightness
 * ends. The light expanded in the HUD renders at full opacity with a white
 * center cross; the rest are dimmed. Wire color is the light's own color,
 * normalized so dark lights stay visible.</p>
 */
@Environment(EnvType.CLIENT)
public class LightShapePreviewRenderer extends GlueDebugRenderer {

    private static final int SEGMENTS = 48;
    private static final int APEX_LINES = 8;

    @Override
    public void render(PoseStack matrices, MultiBufferSource vertexConsumers, double cameraX, double cameraY,
                       double cameraZ) {
        LightDebugHud hud = LightDebugHud.INSTANCE;
        if (!hud.isActive() || !hud.isPreviewEnabled()) return;
        if (Minecraft.getInstance().level == null) return;

        List<Light> lights = LightManager.getInstance().snapshot();
        if (lights.isEmpty()) return;

        PoseStack.Pose pose = matrices.last();

        // All wireframes go into one lines batch first, then the floating text. The two must
        // not interleave: the debug pass shares Minecraft's immediate buffer source, and asking
        // it for the text buffer ends the current lines batch -- so a lines consumer cached
        // across a renderFloatingText call would be flushed mid-loop and throw "Not building!".
        VertexConsumer lines = vertexConsumers.getBuffer(RenderType.lines());
        for (Light light : lights) {
            boolean selected = (light == hud.getSelectedLight());
            int color = wireColor(light, selected);

            float x = (float) (light.x - cameraX);
            float y = (float) (light.y - cameraY);
            float z = (float) (light.z - cameraZ);

            if (light.type == LightType.POINT) {
                drawSphere(pose, lines, x, y, z, light.range, color);
            } else {
                drawCone(pose, lines, light, x, y, z, color, selected);
            }

            cross(pose, lines, x, y, z, 0.25f, selected ? 0xFFFFFFFF : color);
        }

        int index = 0;
        for (Light light : lights) {
            boolean selected = (light == hud.getSelectedLight());
            DebugRenderer.renderFloatingText(matrices, vertexConsumers,
                    "#" + index + " " + light.type,
                    light.x, light.y + 0.5, light.z,
                    selected ? 0xFFFFFF55 : -1, 0.02f, true, 0.0f, true);
            index++;
        }
    }

    // ------------------------------------------------------------------
    // Shapes
    // ------------------------------------------------------------------

    private void drawSphere(PoseStack.Pose pose, VertexConsumer vc,
                            float cx, float cy, float cz, float radius, int color) {
        Vector3f ux = new Vector3f(1, 0, 0);
        Vector3f uy = new Vector3f(0, 1, 0);
        Vector3f uz = new Vector3f(0, 0, 1);
        circle(pose, vc, cx, cy, cz, ux, uy, radius, color);
        circle(pose, vc, cx, cy, cz, ux, uz, radius, color);
        circle(pose, vc, cx, cy, cz, uy, uz, radius, color);
    }

    private void drawCone(PoseStack.Pose pose, VertexConsumer vc, Light light,
                          float ax, float ay, float az, int color, boolean selected) {
        Vector3f dir = new Vector3f(light.directionX, light.directionY, light.directionZ);
        Vector3f up = Math.abs(dir.y) > 0.99f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        Vector3f u = new Vector3f(dir).cross(up).normalize();
        Vector3f v = new Vector3f(u).cross(dir).normalize();

        float outerHalf = (float) Math.acos(Math.clamp(light.cosOuter, -1.0, 1.0));
        float innerHalf = (float) Math.acos(Math.clamp(light.cosInner, -1.0, 1.0));

        // Cap placed so the apex-to-rim wires have length exactly = range:
        // the drawn cone IS the reach boundary along the cone edge.
        drawCap(pose, vc, ax, ay, az, dir, u, v, light.range, outerHalf, color, true);
        if (innerHalf > 0.01f && innerHalf < outerHalf - 0.01f) {
            drawCap(pose, vc, ax, ay, az, dir, u, v, light.range, innerHalf, halfAlpha(color), false);
        }

        // Axis line, so the aim direction reads even edge-on.
        line(pose, vc, ax, ay, az,
                ax + dir.x * light.range, ay + dir.y * light.range, az + dir.z * light.range,
                selected ? 0xFFFFFFFF : color);
    }

    private void drawCap(PoseStack.Pose pose, VertexConsumer vc,
                         float ax, float ay, float az, Vector3f dir, Vector3f u, Vector3f v,
                         float range, float halfAngle, int color, boolean apexLines) {
        float axial = range * (float) Math.cos(halfAngle);
        float radius = range * (float) Math.sin(halfAngle);
        float cx = ax + dir.x * axial;
        float cy = ay + dir.y * axial;
        float cz = az + dir.z * axial;

        circle(pose, vc, cx, cy, cz, u, v, radius, color);

        if (!apexLines) return;
        for (int i = 0; i < APEX_LINES; i++) {
            float ang = (float) (i * Math.PI * 2.0 / APEX_LINES);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            line(pose, vc, ax, ay, az,
                    cx + (u.x * cos + v.x * sin) * radius,
                    cy + (u.y * cos + v.y * sin) * radius,
                    cz + (u.z * cos + v.z * sin) * radius,
                    color);
        }
    }

    private void circle(PoseStack.Pose pose, VertexConsumer vc,
                        float cx, float cy, float cz, Vector3f u, Vector3f v, float radius, int color) {
        float px = 0, py = 0, pz = 0;
        for (int i = 0; i <= SEGMENTS; i++) {
            float ang = (float) (i * Math.PI * 2.0 / SEGMENTS);
            float cos = (float) Math.cos(ang), sin = (float) Math.sin(ang);
            float x = cx + (u.x * cos + v.x * sin) * radius;
            float y = cy + (u.y * cos + v.y * sin) * radius;
            float z = cz + (u.z * cos + v.z * sin) * radius;
            if (i > 0) {
                line(pose, vc, px, py, pz, x, y, z, color);
            }
            px = x;
            py = y;
            pz = z;
        }
    }

    private void cross(PoseStack.Pose pose, VertexConsumer vc,
                       float x, float y, float z, float size, int color) {
        line(pose, vc, x - size, y, z, x + size, y, z, color);
        line(pose, vc, x, y - size, z, x, y + size, z, color);
        line(pose, vc, x, y, z - size, x, y, z + size, color);
    }

    private void line(PoseStack.Pose pose, VertexConsumer vc,
                      float x1, float y1, float z1, float x2, float y2, float z2, int color) {
        float dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-6f) return;
        float nx = dx / len, ny = dy / len, nz = dz / len;
        vc.addVertex(pose, x1, y1, z1).setColor(color).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose, x2, y2, z2).setColor(color).setNormal(pose, nx, ny, nz);
    }

    // ------------------------------------------------------------------
    // Colors
    // ------------------------------------------------------------------

    /** The light's own color normalized to full brightness, dimmed unless selected. */
    private static int wireColor(Light light, boolean selected) {
        float m = Math.max(light.r, Math.max(light.g, light.b));
        float r = m < 0.01f ? 1f : light.r / m;
        float g = m < 0.01f ? 1f : light.g / m;
        float b = m < 0.01f ? 1f : light.b / m;
        int a = selected ? 255 : 110;
        return a << 24 | (int) (r * 255f) << 16 | (int) (g * 255f) << 8 | (int) (b * 255f);
    }

    private static int halfAlpha(int color) {
        int a = (color >>> 24) / 2;
        return (color & 0x00FFFFFF) | a << 24;
    }
}
