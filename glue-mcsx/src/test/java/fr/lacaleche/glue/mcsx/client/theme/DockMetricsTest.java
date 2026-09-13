package fr.lacaleche.glue.mcsx.client.theme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class DockMetricsTest {

    @Test
    void acceptsZeroAndRejectsEveryNegativeField() {
        assertDoesNotThrow(() -> new DockMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        for (int index = 0; index < 11; index++) {
            int field = index;
            assertThrows(IllegalArgumentException.class, () -> metricsWithNegative(field));
        }
    }

    private static DockMetrics metricsWithNegative(int field) {
        int[] values = new int[11];
        values[field] = -1;
        return new DockMetrics(
                values[0], values[1], values[2], values[3], values[4], values[5],
                values[6], values[7], values[8], values[9], values[10]
        );
    }
}
