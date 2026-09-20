package fr.lacaleche.glue.web.internal.host;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostSizingTest {

    @Test
    void theGameScaleGivesOneCssPixelPerGuiPixel() {
        HostSizing.Fit fit = HostSizing.fit(427, 240, 2, 2);
        assertEquals(427, fit.width());
        assertEquals(240, fit.height());
        assertEquals(2d, fit.scale());
    }

    @Test
    void aSmallerWebScaleBuysCssPixelsAndKeepsTheScreenSize() {
        HostSizing.Fit fit = HostSizing.fit(960, 540, 2, 1);
        assertEquals(1920, fit.width());
        assertEquals(1080, fit.height());
        assertEquals(1d, fit.scale());
    }

    @Test
    void aLargerWebScaleEnlargesThePage() {
        HostSizing.Fit fit = HostSizing.fit(800, 600, 2, 4);
        assertEquals(400, fit.width());
        assertEquals(300, fit.height());
        assertEquals(4d, fit.scale());
    }

    @Test
    void aFractionalWebScaleIsRoundedToWholeCssPixels() {
        HostSizing.Fit fit = HostSizing.fit(961, 540, 2, 1.5);
        assertEquals(1281, fit.width());
        assertEquals(720, fit.height());
        assertEquals(1.5, fit.scale());
    }

    @Test
    void reducesTheDensityBeyondTheBrowserLimit() {
        HostSizing.Fit fit = HostSizing.fit(5120, 2880, 1, 1);
        assertEquals(4096, fit.width());
        assertEquals(2304, fit.height());
        assertTrue(fit.scale() <= 1, "The scaled page must stay within the browser limit: " + fit);
    }
}
