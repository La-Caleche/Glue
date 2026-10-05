package fr.lacaleche.glue.client.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UiSliderTest {

    @Test
    void valueRoundsToTheNearestStepFromMin() {
        assertEquals(4.0, UiSlider.snap(4.4, 0.0, 10.0, 1.0));
        assertEquals(5.0, UiSlider.snap(4.5, 0.0, 10.0, 1.0));
        assertEquals(3.0, UiSlider.snap(3.6, 1.0, 9.0, 2.0));
    }

    @Test
    void valueOutsideTheRangeIsClamped() {
        assertEquals(0.0, UiSlider.snap(-3.0, 0.0, 10.0, 1.0));
        assertEquals(10.0, UiSlider.snap(12.0, 0.0, 10.0, 1.0));
    }

    @Test
    void maxIsReachableWhenTheRangeIsNotAWholeNumberOfSteps() {
        assertEquals(10.0, UiSlider.snap(10.0, 0.0, 10.0, 3.0));
    }

    @Test
    void decimalStepsReportTheirDecimalValueWithoutBinaryError() {
        assertEquals(0.3, UiSlider.snap(0.29, 0.0, 1.0, 0.1));
        assertEquals(0.15, UiSlider.snap(0.16, 0.0, 1.0, 0.05));
    }

    @Test
    void valueIsPrintedWithTheStepsDecimals() {
        assertEquals("7", UiSlider.format(7.0, 1.0));
        assertEquals("0.30", UiSlider.format(0.3, 0.05));
        assertEquals("2.5", UiSlider.format(2.5, 0.5));
    }
}
