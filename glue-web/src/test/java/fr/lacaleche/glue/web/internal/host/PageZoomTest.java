package fr.lacaleche.glue.web.internal.host;

import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageZoomTest {

    @Test
    void stepsLikeABrowser() {
        assertEquals(1.1, PageZoom.step(1, true));
        assertEquals(0.9, PageZoom.step(1, false));
        assertEquals(0.67, PageZoom.step(0.5, true));
        assertEquals(0.33, PageZoom.step(0.5, false));
    }

    @Test
    void aDeclaredZoomBetweenTwoStepsMovesToTheNearestInThatDirection() {
        assertEquals(0.67, PageZoom.step(0.6, true));
        assertEquals(0.5, PageZoom.step(0.6, false));
    }

    @Test
    void stopsAtTheBounds() {
        assertEquals(PageZoom.MAX, PageZoom.step(PageZoom.MAX, true));
        assertEquals(PageZoom.MIN, PageZoom.step(PageZoom.MIN, false));
    }

    @Test
    void readsThePhysicalKeysAndTheKeypad() {
        assertEquals(PageZoom.Command.IN, PageZoom.command(GLFW.GLFW_KEY_EQUAL, "="));
        assertEquals(PageZoom.Command.IN, PageZoom.command(GLFW.GLFW_KEY_KP_ADD, null));
        assertEquals(PageZoom.Command.OUT, PageZoom.command(GLFW.GLFW_KEY_MINUS, "-"));
        assertEquals(PageZoom.Command.OUT, PageZoom.command(GLFW.GLFW_KEY_KP_SUBTRACT, null));
        assertEquals(PageZoom.Command.RESET, PageZoom.command(GLFW.GLFW_KEY_0, "0"));
        assertEquals(PageZoom.Command.RESET, PageZoom.command(GLFW.GLFW_KEY_KP_0, null));
    }

    @Test
    void readsTheLayoutWhereThePhysicalKeyDiffers() {
        // AZERTY: minus is on the 6 key, plus is shifted on the equals key.
        assertEquals(PageZoom.Command.OUT, PageZoom.command(GLFW.GLFW_KEY_6, "-"));
        assertEquals(PageZoom.Command.IN, PageZoom.command(GLFW.GLFW_KEY_RIGHT_BRACKET, "+"));
    }

    @Test
    void anyOtherKeyIsNotAZoomKey() {
        assertNull(PageZoom.command(GLFW.GLFW_KEY_6, "6"));
        assertNull(PageZoom.command(GLFW.GLFW_KEY_F5, null));
    }

    @Test
    void keepsAChoicePerOriginLikeABrowserPerSite() {
        assertEquals("https://editor.ignis-editor.glue",
                PageZoom.origin(URI.create("https://editor.ignis-editor.glue/index.html")));
        assertEquals("http://localhost:5173", PageZoom.origin(URI.create("http://localhost:5173/")));
        assertEquals("https://glue-showcase.glue", PageZoom.origin(URI.create("HTTPS://Glue-Showcase.glue/hud/vitals.html")));
    }

    @Test
    void refusesAZoomOutsideItsRange() {
        assertEquals(0.5, PageZoom.validate(0.5));
        assertThrows(IllegalArgumentException.class, () -> PageZoom.validate(0.1));
        assertThrows(IllegalArgumentException.class, () -> PageZoom.validate(5));
        assertThrows(IllegalArgumentException.class, () -> PageZoom.validate(Double.NaN));
    }
}
