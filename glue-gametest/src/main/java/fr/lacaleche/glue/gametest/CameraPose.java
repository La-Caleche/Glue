package fr.lacaleche.glue.gametest;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

/**
 * One camera placement in Minecraft's conventions: yaw 0 looks south (+Z), positive pitch looks down,
 * and the field of view is vertical, in degrees.
 */
public record CameraPose(Vec3 eye, float yaw, float pitch, float fov) {

    public CameraPose {
        Objects.requireNonNull(eye, "eye");
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) throw new IllegalArgumentException("rotation must be finite");
        if (pitch < -90 || pitch > 90) throw new IllegalArgumentException("pitch must lie in [-90, 90]: " + pitch);
        if (!(fov > 0 && fov < 180)) throw new IllegalArgumentException("fov must lie in (0, 180): " + fov);
    }

    /** Interpolates towards {@code other}; the yaw turns the short way round. */
    public CameraPose lerp(CameraPose other, double t) {
        Objects.requireNonNull(other, "other");
        return new CameraPose(
                this.eye.lerp(other.eye, t),
                (float) (this.yaw + Mth.wrapDegrees(other.yaw - this.yaw) * t),
                (float) Mth.lerp(t, this.pitch, other.pitch),
                (float) Mth.lerp(t, this.fov, other.fov));
    }

    /** The pose at {@code eye} that looks straight at {@code target}. */
    public static CameraPose lookingAt(Vec3 eye, Vec3 target, float fov) {
        Vec3 direction = target.subtract(eye);
        double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
        if (horizontal == 0 && direction.y == 0) throw new IllegalArgumentException("target must differ from eye");
        float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(direction.y, horizontal));
        return new CameraPose(eye, yaw, pitch, fov);
    }
}
