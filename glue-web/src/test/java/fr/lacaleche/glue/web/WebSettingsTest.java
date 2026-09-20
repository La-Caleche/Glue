package fr.lacaleche.glue.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class WebSettingsTest {

    @Test
    void refusesAScaleOutsideTheSupportedRange() {
        assertThrows(IllegalArgumentException.class, () -> WebSettings.setScale(WebSettings.MIN_SCALE - 0.25));
        assertThrows(IllegalArgumentException.class, () -> WebSettings.setScale(WebSettings.MAX_SCALE + 0.25));
        assertThrows(IllegalArgumentException.class, () -> WebSettings.setScale(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> WebSettings.setScale(Double.POSITIVE_INFINITY));
    }
}
