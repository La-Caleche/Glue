package fr.lacaleche.glue.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ColorTest {

    @Test
    public void testOpaqueColor() {
        Color color = Color.ofOpaque(0x00FF00); // Pure Green
        assertEquals(255, color.getAlpha(), "Opaque color should have 255 alpha");
        assertEquals(0, color.getRed(), "Red should be 0");
        assertEquals(255, color.getGreen(), "Green should be 255");
        assertEquals(0, color.getBlue(), "Blue should be 0");
    }

    @Test
    public void testRgbFloats() {
        Color color = Color.ofRGB(1.0f, 0.5f, 0.25f);
        assertEquals(255, color.getAlpha());
        assertEquals(255, color.getRed());
        assertEquals(128, color.getGreen()); // 0.5 * 255 = 127.5 -> 128
        assertEquals(64, color.getBlue()); // 0.25 * 255 = 63.75 -> 64
    }

    @Test
    public void testRgbaInts() {
        Color color = Color.ofRGBA(100, 150, 200, 50);
        assertEquals(50, color.getAlpha());
        assertEquals(100, color.getRed());
        assertEquals(150, color.getGreen());
        assertEquals(200, color.getBlue());
    }

    @Test
    public void testHSBtoRGB() {
        Color color = Color.ofHSB(0.0f, 1.0f, 1.0f); // Pure Red
        assertEquals(255, color.getRed());
        assertEquals(0, color.getGreen());
        assertEquals(0, color.getBlue());
        assertEquals(255, color.getAlpha());

        Color colorCyan = Color.ofHSB(0.5f, 1.0f, 1.0f); // Pure Cyan
        assertEquals(0, colorCyan.getRed());
        assertEquals(255, colorCyan.getGreen());
        assertEquals(255, colorCyan.getBlue());

        Color colorWhite = Color.ofHSB(0.0f, 0.0f, 1.0f); // White (0 sat, max brightness)
        assertEquals(255, colorWhite.getRed());
        assertEquals(255, colorWhite.getGreen());
        assertEquals(255, colorWhite.getBlue());
    }

    @Test
    public void testBrighter() {
        Color color = Color.ofRGB(100, 100, 100);
        Color brighter = color.brighter(1.5);

        assertTrue(brighter.getRed() > color.getRed(), "Red should be brighter");
        assertTrue(brighter.getGreen() > color.getGreen(), "Green should be brighter");
        assertTrue(brighter.getBlue() > color.getBlue(), "Blue should be brighter");
        assertEquals(255, color.getAlpha(), "Alpha should remain unchanged");
    }

    @Test
    public void testDarker() {
        Color color = Color.ofRGB(100, 100, 100);
        Color darker = color.darker(1.5);

        assertTrue(darker.getRed() < color.getRed(), "Red should be darker");
        assertTrue(darker.getGreen() < color.getGreen(), "Green should be darker");
        assertTrue(darker.getBlue() < color.getBlue(), "Blue should be darker");
        assertEquals(255, color.getAlpha(), "Alpha should remain unchanged");
    }

    @Test
    public void testEqualsAndHashCode() {
        Color color1 = Color.ofRGBA(10, 20, 30, 40);
        Color color2 = Color.ofRGBA(10, 20, 30, 40);
        Color color3 = Color.ofRGBA(10, 20, 30, 41);

        assertEquals(color1, color2, "Colors with exact RGBA should be equal");
        assertEquals(color1.hashCode(), color2.hashCode(), "HashCodes should match");
        assertNotEquals(color1, color3, "Colors with different RGBA should not be equal");
    }
}
