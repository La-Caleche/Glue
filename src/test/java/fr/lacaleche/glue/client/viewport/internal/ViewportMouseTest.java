package fr.lacaleche.glue.client.viewport.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ViewportMouseTest {

    @Test
    void wholeWindowViewportAtScaleOneIsIdentity() {
        assertEquals(725.0, ViewportMouse.toViewportGui(725.0, 1920, 1920, 0, 1), 1e-9);
    }

    @Test
    void offsetViewportSubtractsItsOriginBeforeScaling() {
        assertEquals(100.0, ViewportMouse.toViewportGui(500.0, 1920, 1920, 300, 2), 1e-9);
    }

    @Test
    void highDpiCursorIsScaledFromScreenToFramebufferFirst() {
        // A 2x display: 960 screen coordinates cover 1920 framebuffer pixels.
        assertEquals(330.0, ViewportMouse.toViewportGui(480.0, 960, 1920, 300, 2), 1e-9);
    }

    @Test
    void viewportEdgesMapToItsOwnGuiExtentRegardlessOfViewportSize() {
        // The regression this class exists for: the conversion must not depend on the viewport's
        // width the way a conversion built on the narrowed window GUI size did. A cursor on the
        // viewport's left and right edges maps to 0 and width/scale for any viewport size.
        for (int viewportWidth : new int[] {120, 640, 1300}) {
            int originX = 260;
            double leftRaw = rawFor(originX, 1920, 1920);
            double rightRaw = rawFor(originX + viewportWidth, 1920, 1920);
            assertEquals(0.0, ViewportMouse.toViewportGui(leftRaw, 1920, 1920, originX, 2), 1e-9);
            assertEquals(viewportWidth / 2.0,
                    ViewportMouse.toViewportGui(rightRaw, 1920, 1920, originX, 2), 1e-9);
        }
    }

    @Test
    void conversionInvertsTheRenderTransformIncludingNonDivisibleDimensions() {
        // A viewport GUI coordinate u renders at originX + u * guiScale framebuffer pixels; the
        // pointer conversion must be that transform's inverse, including when neither the window nor
        // the viewport divides evenly by the GUI scale and the display is high-DPI.
        int screenWidth = 1003;
        int frameWidth = 1505;
        int originX = 37;
        int guiScale = 3;
        for (double gui : new double[] {0.0, 13.5, 250.25}) {
            double raw = rawFor(originX + gui * guiScale, frameWidth, screenWidth);
            assertEquals(gui, ViewportMouse.toViewportGui(raw, screenWidth, frameWidth, originX, guiScale), 1e-9);
        }
    }

    @Test
    void deltaUsesTheSameHighDpiAndNonDivisibleScaleAsAbsoluteCoordinates() {
        int screenWidth = 1003;
        int frameWidth = 1505;
        int guiScale = 3;
        double rawDelta = 17.25;

        double start = ViewportMouse.toViewportGui(200.0, screenWidth, frameWidth, 37, guiScale);
        double end = ViewportMouse.toViewportGui(200.0 + rawDelta, screenWidth, frameWidth, 37, guiScale);

        assertEquals(end - start,
                ViewportMouse.deltaToViewportGui(rawDelta, screenWidth, frameWidth, guiScale), 1e-9);
    }

    /** The screen-coordinate cursor position that lands on the given framebuffer coordinate. */
    private static double rawFor(double framebuffer, int frameExtent, int screenExtent) {
        return framebuffer * screenExtent / frameExtent;
    }
}
