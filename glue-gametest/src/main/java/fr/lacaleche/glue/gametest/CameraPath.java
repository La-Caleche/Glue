package fr.lacaleche.glue.gametest;

import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.DoubleFunction;

/**
 * A camera move over film time. Paths are immutable and chain with {@link #then}; a time outside
 * {@code [0, seconds]} holds the nearest end.
 */
public final class CameraPath {

    private final double seconds;
    private final DoubleFunction<CameraPose> pose;

    private CameraPath(double seconds, DoubleFunction<CameraPose> pose) {
        if (!(seconds > 0) || Double.isInfinite(seconds)) throw new IllegalArgumentException("seconds must be positive: " + seconds);
        this.seconds = seconds;
        this.pose = pose;
    }

    public double seconds() {
        return this.seconds;
    }

    public CameraPose at(double time) {
        return this.pose.apply(Math.clamp(time, 0, this.seconds));
    }

    /** Plays {@code next} once this path ends. */
    public CameraPath then(CameraPath next) {
        Objects.requireNonNull(next, "next");
        double split = this.seconds;
        return new CameraPath(split + next.seconds, time -> time <= split ? this.at(time) : next.at(time - split));
    }

    /** A still shot. */
    public static CameraPath hold(CameraPose pose, double seconds) {
        Objects.requireNonNull(pose, "pose");
        return new CameraPath(seconds, time -> pose);
    }

    /** Eases from one pose to another, starting and stopping gently. */
    public static CameraPath move(CameraPose from, CameraPose to, double seconds) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return new CameraPath(seconds, time -> from.lerp(to, smooth(time / seconds)));
    }

    /** Eases the eye from one point to another while it keeps looking at {@code target}. */
    public static CameraPath track(Vec3 from, Vec3 to, Vec3 target, float fov, double seconds) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(target, "target");
        CameraPose.lookingAt(from, target, fov);
        CameraPose.lookingAt(to, target, fov);
        return new CameraPath(seconds, time -> CameraPose.lookingAt(from.lerp(to, smooth(time / seconds)), target, fov));
    }

    /**
     * Circles {@code center} at constant speed, {@code height} blocks above it, looking at it. Angle 0
     * places the camera south (+Z) of the centre; positive angles turn towards west (-X).
     */
    public static CameraPath orbit(Vec3 center, double radius, double height, double fromDegrees, double toDegrees,
                                   float fov, double seconds) {
        Objects.requireNonNull(center, "center");
        if (!(radius > 0)) throw new IllegalArgumentException("radius must be positive: " + radius);
        return new CameraPath(seconds, time -> {
            double angle = Math.toRadians(fromDegrees + (toDegrees - fromDegrees) * time / seconds);
            Vec3 eye = center.add(-Math.sin(angle) * radius, height, Math.cos(angle) * radius);
            return CameraPose.lookingAt(eye, center, fov);
        });
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }
}
