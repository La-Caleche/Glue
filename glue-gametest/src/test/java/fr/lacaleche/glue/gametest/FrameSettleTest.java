package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FrameSettleTest {

    @Test
    void countsFramesFromTheFirstObservationRatherThanFromZero() {
        FrameSettle settle = new FrameSettle(3);

        assertFalse(settle.settled(1000));
        assertFalse(settle.settled(1002));
        assertTrue(settle.settled(1003));
    }

    @Test
    void staysOpenWhileNothingIsDrawn() {
        FrameSettle settle = new FrameSettle(2);
        settle.settled(10);

        for (int tick = 0; tick < 100; tick++) {
            assertFalse(settle.settled(10));
        }
        assertTrue(settle.settled(12));
    }

    @Test
    void settlesImmediatelyWhenNoFrameIsRequired() {
        assertTrue(new FrameSettle(0).settled(7));
    }

    @Test
    void rejectsANegativeFrameRequirement() {
        assertThrows(IllegalArgumentException.class, () -> new FrameSettle(-1));
    }
}
