package fr.lacaleche.glue.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SeedUtilTest {

    @Test
    public void testTimePosSeedStability() {
        long now = 10000L;
        long window = 1000L;
        double posQuant = 1.0;

        int seed1 = SeedUtil.timePosSeed(now, window, 5.0, 10.0, 15.0, posQuant);
        int seed2 = SeedUtil.timePosSeed(now, window, 5.0, 10.0, 15.0, posQuant);

        assertEquals(seed1, seed2, "Seed must be deterministic for identical inputs");
    }

    @Test
    public void testTimePosSeedQuantization() {
        long now = 10000L;
        long window = 1000L;
        double posQuant = 0.5;

        int seedA = SeedUtil.timePosSeed(now, window, 5.1, 10.1, 15.1, posQuant);
        int seedB = SeedUtil.timePosSeed(now, window, 5.2, 10.3, 15.2, posQuant);

        assertEquals(seedA, seedB, "Positions within the same quantization boundary must share the same seed");
    }

    @Test
    public void testTimePosSeedBoundaryCross() {
        long now = 10000L;
        long window = 1000L;
        double posQuant = 1.0;

        int seedA = SeedUtil.timePosSeed(now, window, 5.5, 10.5, 15.5, posQuant);
        int seedB = SeedUtil.timePosSeed(now, window, 6.5, 10.5, 15.5, posQuant); // Crossed X boundary

        assertNotEquals(seedA, seedB, "Crossing a positional quantization boundary must yield a different seed");
    }

    @Test
    public void testTimePosSeedTimeWindowCross() {
        long window = 1000L;
        double posQuant = 1.0;

        int seedA = SeedUtil.timePosSeed(1050L, window, 5.0, 10.0, 15.0, posQuant); // Bucket 1
        int seedB = SeedUtil.timePosSeed(2050L, window, 5.0, 10.0, 15.0, posQuant); // Bucket 2

        assertNotEquals(seedA, seedB, "Crossing a time window boundary must yield a different seed");
    }

    @Test
    public void testIntFoldingIsWide() {
        int s1 = SeedUtil.timePosSeed(1000L, 100L, 1.0, 1.0, 1.0, 1.0);
        int s2 = SeedUtil.timePosSeed(1000L, 100L, 2.0, 1.0, 1.0, 1.0);
        int s3 = SeedUtil.timePosSeed(1000L, 100L, 3.0, 1.0, 1.0, 1.0);

        assertTrue(s1 != 0 && s2 != 0 && s3 != 0, "Seeds should not be entirely uniformly zeroed");
        assertTrue(s1 != s2 && s2 != s3 && s1 != s3, "Neighboring positions should yield uniquely scattered seeds");
    }
}
