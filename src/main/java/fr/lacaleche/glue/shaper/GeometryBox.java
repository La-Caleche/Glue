package fr.lacaleche.glue.shaper;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

import java.util.Objects;

/**
 * One box of a {@link ShapeGeometry}, in block units, optionally rotated about one axis the way a
 * block model element is.
 *
 * @param box      the box before its rotation
 * @param rotation the element rotation, or {@code null} for an axis-aligned box
 */
public record GeometryBox(AABB box, @Nullable Rotation rotation) {

    public GeometryBox {
        Objects.requireNonNull(box, "box");
    }

    /** An axis-aligned box. */
    public static GeometryBox of(AABB box) {
        return new GeometryBox(box, null);
    }

    /** An axis-aligned box in pixels, a sixteenth of a block, like {@code Block.box}. */
    public static GeometryBox pixels(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return of(new AABB(minX / 16, minY / 16, minZ / 16, maxX / 16, maxY / 16, maxZ / 16));
    }

    /** This box with the given element rotation. */
    public GeometryBox rotated(Rotation rotation) {
        return new GeometryBox(this.box, rotation);
    }

    /** The matrix taking this box's coordinates into geometry space: identity without a rotation. */
    Matrix4f matrix() {
        return this.rotation == null ? new Matrix4f() : this.rotation.matrix();
    }

    /**
     * A block model element rotation: {@code degrees} about {@code axis} through {@code origin}, in
     * block units. With {@code rescale}, the two other axes stretch by {@code 1 / cos(degrees)}, as
     * vanilla model baking does, so a rotated element still spans the block.
     */
    public record Rotation(Vec3 origin, Direction.Axis axis, float degrees, boolean rescale) {

        public Rotation {
            Objects.requireNonNull(origin, "origin");
            Objects.requireNonNull(axis, "axis");
        }

        Matrix4f matrix() {
            float stretch = this.rescale && this.degrees != 0f
                    ? 1f / (float) Math.cos(Math.toRadians(Math.abs(this.degrees)))
                    : 1f;
            Matrix4f matrix = new Matrix4f().translation((float) this.origin.x, (float) this.origin.y, (float) this.origin.z);
            matrix.scale(
                    this.axis == Direction.Axis.X ? 1f : stretch,
                    this.axis == Direction.Axis.Y ? 1f : stretch,
                    this.axis == Direction.Axis.Z ? 1f : stretch);
            matrix.rotate((float) Math.toRadians(this.degrees), this.axis.getPositive().getUnitVec3f());
            return matrix.translate((float) -this.origin.x, (float) -this.origin.y, (float) -this.origin.z);
        }
    }
}
