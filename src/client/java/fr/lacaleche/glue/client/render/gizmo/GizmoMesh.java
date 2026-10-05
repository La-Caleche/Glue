package fr.lacaleche.glue.client.render.gizmo;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.Arrays;

/**
 * A gizmo's triangles in camera-relative coordinates, shaded once from a light beside the camera and
 * drawn back to front, since they are translucent and do not write depth.
 */
final class GizmoMesh {

    private final Vector3fc forward;
    private final Vector3f light;
    private float[] positions = new float[9 * 256];
    private int[] colors = new int[256];
    private int count;

    GizmoMesh(GizmoView view) {
        this.forward = view.forward();
        this.light = new Vector3f(view.forward()).negate().add(new Vector3f(view.up()).mul(0.6F))
                .add(new Vector3f(view.right()).mul(0.3F)).normalize();
    }

    int size() {
        return this.count;
    }

    /** A triangle shaded by how squarely it faces the light, from either side. */
    void triangle(Vector3fc a, Vector3fc b, Vector3fc c, int color) {
        Vector3f normal = new Vector3f(b).sub(a).cross(new Vector3f(c).sub(a));
        float length = normal.length();
        float facing = length < 1.0E-12F ? 1.0F : Math.abs(normal.dot(this.light)) / length;
        float shade = 0.55F + 0.45F * facing;
        this.add(a, b, c, ARGB.color(ARGB.alpha(color), (int) (ARGB.red(color) * shade),
                (int) (ARGB.green(color) * shade), (int) (ARGB.blue(color) * shade)));
    }

    /** A triangle in its own colour, unshaded. */
    void flat(Vector3fc a, Vector3fc b, Vector3fc c, int color) {
        this.add(a, b, c, color);
    }

    void quad(Vector3fc a, Vector3fc b, Vector3fc c, Vector3fc d, int color) {
        this.triangle(a, b, c, color);
        this.triangle(a, c, d, color);
    }

    /** An open tube from one point to another. */
    void tube(Vector3fc from, Vector3fc to, float radius, int sides, int color) {
        Vector3f axis = new Vector3f(to).sub(from).normalize();
        Vector3f u = perpendicular(axis);
        Vector3f v = new Vector3f(axis).cross(u);
        Vector3f[] start = new Vector3f[sides];
        Vector3f[] end = new Vector3f[sides];
        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;
            Vector3f offset = new Vector3f(u).mul((float) Math.cos(angle) * radius)
                    .add(new Vector3f(v).mul((float) Math.sin(angle) * radius));
            start[i] = new Vector3f(from).add(offset);
            end[i] = new Vector3f(to).add(offset);
        }
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            this.quad(start[i], start[next], end[next], end[i], color);
        }
    }

    /** A closed cone from a base disc to its tip. */
    void cone(Vector3fc base, Vector3fc tip, float radius, int sides, int color) {
        Vector3f axis = new Vector3f(tip).sub(base).normalize();
        Vector3f u = perpendicular(axis);
        Vector3f v = new Vector3f(axis).cross(u);
        Vector3f[] rim = new Vector3f[sides];
        for (int i = 0; i < sides; i++) {
            double angle = 2.0 * Math.PI * i / sides;
            rim[i] = new Vector3f(base).add(new Vector3f(u).mul((float) Math.cos(angle) * radius))
                    .add(new Vector3f(v).mul((float) Math.sin(angle) * radius));
        }
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            this.triangle(rim[i], rim[next], tip, color);
            this.triangle(rim[next], rim[i], base, color);
        }
    }

    /** A cube around a centre, its faces along the three axes. */
    void cube(Vector3fc center, Vector3fc[] axes, float half, int color) {
        Vector3f[] corners = new Vector3f[8];
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f(center)
                    .add(new Vector3f(axes[0]).mul((i & 1) == 0 ? -half : half))
                    .add(new Vector3f(axes[1]).mul((i & 2) == 0 ? -half : half))
                    .add(new Vector3f(axes[2]).mul((i & 4) == 0 ? -half : half));
        }
        this.quad(corners[0], corners[1], corners[3], corners[2], color);
        this.quad(corners[4], corners[5], corners[7], corners[6], color);
        this.quad(corners[0], corners[1], corners[5], corners[4], color);
        this.quad(corners[2], corners[3], corners[7], corners[6], color);
        this.quad(corners[0], corners[2], corners[6], corners[4], color);
        this.quad(corners[1], corners[3], corners[7], corners[5], color);
    }

    /** A ring as a thin tube around an axis, through the points {@code center + radius * (u cos + v sin)}. */
    void ring(Vector3fc center, Vector3fc u, Vector3fc v, float radius, float thickness, int segments, int color) {
        Vector3f normal = new Vector3f(u).cross(v).normalize();
        int sides = 6;
        Vector3f[][] points = new Vector3f[segments][sides];
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            Vector3f outward = new Vector3f(u).mul((float) Math.cos(angle)).add(new Vector3f(v).mul((float) Math.sin(angle)));
            Vector3f middle = new Vector3f(center).add(new Vector3f(outward).mul(radius));
            for (int j = 0; j < sides; j++) {
                double around = 2.0 * Math.PI * j / sides;
                points[i][j] = new Vector3f(middle)
                        .add(new Vector3f(outward).mul((float) Math.cos(around) * thickness))
                        .add(new Vector3f(normal).mul((float) Math.sin(around) * thickness));
            }
        }
        for (int i = 0; i < segments; i++) {
            int nextSegment = (i + 1) % segments;
            for (int j = 0; j < sides; j++) {
                int nextSide = (j + 1) % sides;
                this.quad(points[i][j], points[i][nextSide], points[nextSegment][nextSide], points[nextSegment][j],
                        color);
            }
        }
    }

    /**
     * The triangles' order from the farthest to the nearest, by their centres' distance along the view.
     */
    int[] backToFront() {
        Integer[] order = new Integer[this.count];
        float[] depths = new float[this.count];
        for (int i = 0; i < this.count; i++) {
            order[i] = i;
            int at = i * 9;
            depths[i] = (this.positions[at] + this.positions[at + 3] + this.positions[at + 6]) * this.forward.x()
                    + (this.positions[at + 1] + this.positions[at + 4] + this.positions[at + 7]) * this.forward.y()
                    + (this.positions[at + 2] + this.positions[at + 5] + this.positions[at + 8]) * this.forward.z();
        }
        Arrays.sort(order, (a, b) -> Float.compare(depths[b], depths[a]));
        int[] sorted = new int[this.count];
        for (int i = 0; i < this.count; i++) {
            sorted[i] = order[i];
        }
        return sorted;
    }

    /** Writes the triangles in order, their alpha scaled, as camera-relative positions with a colour. */
    void emit(VertexConsumer consumer, int[] order, float alpha) {
        for (int triangle : order) {
            int color = this.color(triangle, alpha);
            for (int corner = 0; corner < 3; corner++) {
                int at = triangle * 9 + corner * 3;
                consumer.addVertex(this.positions[at], this.positions[at + 1], this.positions[at + 2]).setColor(color);
            }
        }
    }

    void corner(int triangle, int corner, Vector3f out) {
        int at = triangle * 9 + corner * 3;
        out.set(this.positions[at], this.positions[at + 1], this.positions[at + 2]);
    }

    int color(int triangle, float alpha) {
        int color = this.colors[triangle];
        return ARGB.color(Math.round(ARGB.alpha(color) * alpha), color);
    }

    private void add(Vector3fc a, Vector3fc b, Vector3fc c, int color) {
        if (this.count == this.colors.length) {
            this.colors = Arrays.copyOf(this.colors, this.count * 2);
            this.positions = Arrays.copyOf(this.positions, this.count * 2 * 9);
        }
        int at = this.count * 9;
        this.positions[at] = a.x();
        this.positions[at + 1] = a.y();
        this.positions[at + 2] = a.z();
        this.positions[at + 3] = b.x();
        this.positions[at + 4] = b.y();
        this.positions[at + 5] = b.z();
        this.positions[at + 6] = c.x();
        this.positions[at + 7] = c.y();
        this.positions[at + 8] = c.z();
        this.colors[this.count] = color;
        this.count++;
    }

    /** A unit vector perpendicular to a unit axis. */
    static Vector3f perpendicular(Vector3fc axis) {
        Vector3f helper = Math.abs(axis.y()) < 0.9F ? new Vector3f(0.0F, 1.0F, 0.0F) : new Vector3f(1.0F, 0.0F, 0.0F);
        return helper.cross(axis).normalize();
    }
}
