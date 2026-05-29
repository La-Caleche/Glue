package fr.lacaleche.glue.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SeedUtilTest {

    @Test
    public void timePosSeed_identicalInputs_returnsSameValue() {
        int seed1 = SeedUtil.timePosSeed(10000L, 1000L, 5.0, 10.0, 15.0, 1.0);
        int seed2 = SeedUtil.timePosSeed(10000L, 1000L, 5.0, 10.0, 15.0, 1.0);
        assertEquals(seed1, seed2, "Seed must be deterministic for identical inputs");
    }

    @Test
    public void timePosSeed_positionsInSameBucket_returnsSameValue() {
        int seedA = SeedUtil.timePosSeed(10000L, 1000L, 5.1, 10.1, 15.1, 0.5);
        int seedB = SeedUtil.timePosSeed(10000L, 1000L, 5.2, 10.3, 15.2, 0.5);
        assertEquals(seedA, seedB, "Positions in the same quantization bucket must share the same seed");
    }

    @Test
    public void timePosSeed_positionAcrossQuantBoundary_returnsDifferentValue() {
        int seedA = SeedUtil.timePosSeed(10000L, 1000L, 5.5, 10.5, 15.5, 1.0);
        int seedB = SeedUtil.timePosSeed(10000L, 1000L, 6.5, 10.5, 15.5, 1.0);
        assertNotEquals(seedA, seedB, "x=6.5 crosses the x=6.0 quantization boundary");
    }

    @Test
    public void timePosSeed_timeAcrossWindowBoundary_returnsDifferentValue() {
        int seedA = SeedUtil.timePosSeed(1050L, 1000L, 5.0, 10.0, 15.0, 1.0);
        int seedB = SeedUtil.timePosSeed(2050L, 1000L, 5.0, 10.0, 15.0, 1.0);
        assertNotEquals(seedA, seedB, "Crossing a time window boundary must yield a different seed");
    }

    @Test
    public void timePosSeed_neighboringPositions_returnDistinctValues() {
        int s1 = SeedUtil.timePosSeed(1000L, 100L, 1.0, 1.0, 1.0, 1.0);
        int s2 = SeedUtil.timePosSeed(1000L, 100L, 2.0, 1.0, 1.0, 1.0);
        int s3 = SeedUtil.timePosSeed(1000L, 100L, 3.0, 1.0, 1.0, 1.0);
        assertTrue(s1 != s2 && s2 != s3 && s1 != s3, "Neighboring positions should yield distinct seeds");
    }

    @Test
    public void timePosSeed_negativeCoordinates_doesNotThrow() {
        assertDoesNotThrow(() -> SeedUtil.timePosSeed(1000L, 1000L, -5.0, -10.0, -15.0, 1.0));
    }

    @Test
    public void timePosSeed_windowMsZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> SeedUtil.timePosSeed(1000L, 0L, 0.0, 0.0, 0.0, 1.0));
    }

    @Test
    public void timePosSeed_windowMsNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> SeedUtil.timePosSeed(1000L, -1L, 0.0, 0.0, 0.0, 1.0));
    }

    @Test
    public void timePosSeed_posQuantZero_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> SeedUtil.timePosSeed(1000L, 1000L, 0.0, 0.0, 0.0, 0.0));
    }

    @Test
    public void timePosSeed_posQuantNegative_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> SeedUtil.timePosSeed(1000L, 1000L, 0.0, 0.0, 0.0, -1.0));
    }

    @Test
    public void timePosSeed_fnvAlgorithmOrder_xorBeforeMultiply() {
        int seedA = SeedUtil.timePosSeed(1000L, 1000L, 0.0, 0.0, 0.0, 1.0);
        int seedB = SeedUtil.timePosSeed(2000L, 1000L, 0.0, 0.0, 0.0, 1.0);
        assertNotEquals(seedA, seedB,
                "Different time buckets must produce different seeds (FNV-1a ordering check)");
    }
}
