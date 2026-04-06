package fr.lacaleche.glue.math;

public final class SeedUtil {

    private static int fnv1a64ToInt(long... vals) {
        long h = 0xcbf29ce484222325L;
        for (long v : vals) {
            h ^= v;
            h *= 0x100000001b3L;
        }
        h ^= (h >>> 32);
        return (int) h;
    }

    public static int timePosSeed(long windowMs, double x, double y, double z, double posQuant) {
        return timePosSeed(System.currentTimeMillis(), windowMs, x, y, z, posQuant);
    }

    public static int timePosSeed(long nowMs, long windowMs, double x, double y, double z, double posQuant) {
        long bucket = nowMs / windowMs;
        long qx = (long) Math.floor(x / posQuant);
        long qy = (long) Math.floor(y / posQuant);
        long qz = (long) Math.floor(z / posQuant);
        return fnv1a64ToInt(bucket, qx, qy, qz);
    }
}