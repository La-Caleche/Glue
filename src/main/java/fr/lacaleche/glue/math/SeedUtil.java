package fr.lacaleche.glue.math;

public final class SeedUtil {
    /**
     * FNV-1a 64-bit (folded to int) for a quick, decent hash
     */
    private static int fnv1a64ToInt(long... vals) {
        long h = 0xcbf29ce484222325L;
        for (long v : vals) {
            h ^= v;
            h *= 0x100000001b3L;
        }
        // final avalanching + fold to int
        h ^= (h >>> 32);
        return (int) h;
    }

    public static int timePosSeed(long windowMs, double x, double y, double z, double posQuant) {
        return timePosSeed(System.currentTimeMillis(), windowMs, x, y, z, posQuant);
    }

    /**
     * Seed that changes every {@code windowMs}, and varies by world position.
     * Position is quantized to {@code posQuant} units to avoid tiny jitter causing a new seed.
     */
    public static int timePosSeed(long nowMs, long windowMs, double x, double y, double z, double posQuant) {
        long bucket = nowMs / windowMs;                 // e.g., one value per minute/window
        // Quantize position so neighboring particles can still share a seed if desired
        long qx = (long) Math.floor(x / posQuant);
        long qy = (long) Math.floor(y / posQuant);
        long qz = (long) Math.floor(z / posQuant);
        return fnv1a64ToInt(bucket, qx, qy, qz);
    }
}