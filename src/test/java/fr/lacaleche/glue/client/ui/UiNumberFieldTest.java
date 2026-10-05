package fr.lacaleche.glue.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiNumberFieldTest {

    @Test
    void valueRoundsToTheNearestMultipleOfTheStep() {
        assertEquals(12.4, UiNumberField.snap(12.3456 + 0.1, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1));
        assertEquals(-3.0, UiNumberField.snap(-2.6, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 1.0));
        assertEquals(0.3, UiNumberField.snap(0.1 + 0.2, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0.1));
    }

    @Test
    void boundedValueIsClamped() {
        assertEquals(0.5, UiNumberField.snap(0.2, 0.5, 128.0, 0.5));
        assertEquals(128.0, UiNumberField.snap(300.0, 0.5, 128.0, 0.5));
    }

    @Test
    void valueIsPrintedWithTheStepsDecimalsOrUpToTwoMore() {
        assertEquals("4.0", UiNumberField.format(4.0, 0.1));
        assertEquals("12.346", UiNumberField.format(12.34567, 0.1));
        assertEquals("7", UiNumberField.format(7.0, 1.0));
        assertEquals("7.25", UiNumberField.format(7.25, 1.0));
    }
}
