package fr.lacaleche.glue.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ColorTest {

    @Test
    public void ofTransparent_rawPackedInt_preservesAllBits() {
        Color color = Color.ofTransparent(0x80FF0000);
        assertEquals(0x80, color.getAlpha());
        assertEquals(0xFF, color.getRed());
        assertEquals(0, color.getGreen());
        assertEquals(0, color.getBlue());
    }

    @Test
    public void ofOpaque_anyRgb_forcesFullAlpha() {
        Color color = Color.ofOpaque(0x00FF00);
        assertEquals(255, color.getAlpha());
        assertEquals(0, color.getRed());
        assertEquals(255, color.getGreen());
        assertEquals(0, color.getBlue());
    }

    @Test
    public void ofRGB_intOverload_forcesFullAlpha() {
        Color color = Color.ofRGB(10, 20, 30);
        assertEquals(255, color.getAlpha());
        assertEquals(10, color.getRed());
        assertEquals(20, color.getGreen());
        assertEquals(30, color.getBlue());
    }

    @Test
    public void ofRGB_floatOverload_roundsCorrectly() {
        Color color = Color.ofRGB(1.0f, 0.5f, 0.25f);
        assertEquals(255, color.getAlpha());
        assertEquals(255, color.getRed());
        assertEquals(128, color.getGreen(), "0.5 * 255 + 0.5 rounds to 128");
        assertEquals(64, color.getBlue(), "0.25 * 255 + 0.5 rounds to 64");
    }

    @Test
    public void ofRGBA_floatOverload_roundsAlphaCorrectly() {
        Color color = Color.ofRGBA(1.0f, 0.0f, 0.0f, 0.5f);
        assertEquals(128, color.getAlpha(), "0.5 * 255 + 0.5 rounds to 128");
        assertEquals(255, color.getRed());
        assertEquals(0, color.getGreen());
        assertEquals(0, color.getBlue());
    }

    @Test
    public void ofRGBA_intOverload_preservesAllChannels() {
        Color color = Color.ofRGBA(100, 150, 200, 50);
        assertEquals(50, color.getAlpha());
        assertEquals(100, color.getRed());
        assertEquals(150, color.getGreen());
        assertEquals(200, color.getBlue());
    }

    @Test
    public void ofHSB_hue0FullSaturation_givesRed() {
        Color color = Color.ofHSB(0.0f, 1.0f, 1.0f);
        assertEquals(255, color.getAlpha());
        assertEquals(255, color.getRed());
        assertEquals(0, color.getGreen());
        assertEquals(0, color.getBlue());
    }

    @Test
    public void ofHSB_hueHalfFullSaturation_givesCyan() {
        Color color = Color.ofHSB(0.5f, 1.0f, 1.0f);
        assertEquals(255, color.getAlpha());
        assertEquals(0, color.getRed());
        assertEquals(255, color.getGreen());
        assertEquals(255, color.getBlue());
    }

    @Test
    public void ofHSB_zeroSaturation_givesWhite() {
        Color color = Color.ofHSB(0.0f, 0.0f, 1.0f);
        assertEquals(255, color.getAlpha());
        assertEquals(255, color.getRed());
        assertEquals(255, color.getGreen());
        assertEquals(255, color.getBlue());
    }

    @Test
    public void brighter_normalColor_scalesChannelsUp() {
        Color color = Color.ofRGB(100, 100, 100);
        Color brighter = color.brighter(1.5);
        assertEquals(150, brighter.getRed());
        assertEquals(150, brighter.getGreen());
        assertEquals(150, brighter.getBlue());
        assertEquals(255, brighter.getAlpha(), "Alpha must remain unchanged");
    }

    @Test
    public void brighter_channelWouldExceed255_clampsTo255() {
        Color color = Color.ofRGB(200, 200, 200);
        Color brighter = color.brighter(2.0);
        assertEquals(255, brighter.getRed());
        assertEquals(255, brighter.getGreen());
        assertEquals(255, brighter.getBlue());
    }

    @Test
    public void brighter_blackColor_raisesToMinThreshold() {
        Color color = Color.ofRGB(0, 0, 0);
        Color brighter = color.brighter(2.0);
        assertTrue(brighter.getRed() > 0, "Black R should be raised to minThreshold");
        assertTrue(brighter.getGreen() > 0, "Black G should be raised to minThreshold");
        assertTrue(brighter.getBlue() > 0, "Black B should be raised to minThreshold");
    }

    @Test
    public void brighter_factorAtMostOne_throwsIllegalArgument() {
        Color color = Color.ofRGB(100, 100, 100);
        assertThrows(IllegalArgumentException.class, () -> color.brighter(1.0), "factor == 1.0 should throw");
        assertThrows(IllegalArgumentException.class, () -> color.brighter(0.5), "factor < 1.0 should throw");
    }

    @Test
    public void darker_normalColor_scalesChannelsDown() {
        Color color = Color.ofRGB(100, 100, 100);
        Color darker = color.darker(1.5);
        assertEquals(66, darker.getRed(), "floor(100 / 1.5) = 66");
        assertEquals(66, darker.getGreen());
        assertEquals(66, darker.getBlue());
        assertEquals(255, darker.getAlpha(), "Alpha must remain unchanged");
    }

    @Test
    public void darker_veryDarkColor_clampsTo0() {
        Color color = Color.ofRGB(1, 1, 1);
        Color darker = color.darker(100.0);
        assertEquals(0, darker.getRed());
        assertEquals(0, darker.getGreen());
        assertEquals(0, darker.getBlue());
    }

    @Test
    public void darker_factorAtMostOne_throwsIllegalArgument() {
        Color color = Color.ofRGB(100, 100, 100);
        assertThrows(IllegalArgumentException.class, () -> color.darker(1.0), "factor == 1.0 should throw");
        assertThrows(IllegalArgumentException.class, () -> color.darker(0.5), "factor < 1.0 should throw");
        assertThrows(IllegalArgumentException.class, () -> color.darker(0.0), "factor == 0.0 should throw");
        assertThrows(IllegalArgumentException.class, () -> color.darker(-1.0), "factor < 0 should throw");
    }

    @Test
    public void equals_identicalComponents_areEqual() {
        Color a = Color.ofRGBA(10, 20, 30, 40);
        Color b = Color.ofRGBA(10, 20, 30, 40);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void equals_differentAlpha_areNotEqual() {
        Color a = Color.ofRGBA(10, 20, 30, 40);
        Color b = Color.ofRGBA(10, 20, 30, 41);
        assertNotEquals(a, b);
    }

    @Test
    public void equals_null_returnsFalse() {
        assertNotEquals(null, Color.ofRGB(1, 2, 3));
    }

    @Test
    public void equals_differentType_returnsFalse() {
        assertNotEquals(Color.ofRGB(1, 2, 3), "not a color");
    }

    @Test
    public void toString_anyColor_isAaRrGgBbHexFormat() {
        assertEquals("#FFFF0000", Color.ofRGBA(255, 0, 0, 255).toString(), "Format should be #AARRGGBB");
        assertEquals("#00000000", Color.ofRGBA(0, 0, 0, 0).toString());
        assertEquals("#FF00FF00", Color.ofRGBA(0, 255, 0, 255).toString());
    }

    @Test
    public void constant_BLACK_hasExpectedChannelValues() {
        assertEquals(0, Color.BLACK.getRed());
        assertEquals(0, Color.BLACK.getGreen());
        assertEquals(0, Color.BLACK.getBlue());
        assertEquals(255, Color.BLACK.getAlpha());
    }

    @Test
    public void constant_WHITE_hasExpectedChannelValues() {
        assertEquals(255, Color.WHITE.getRed());
        assertEquals(255, Color.WHITE.getGreen());
        assertEquals(255, Color.WHITE.getBlue());
        assertEquals(255, Color.WHITE.getAlpha());
    }

    @Test
    public void constant_RED_hasExpectedChannelValues() {
        assertEquals(255, Color.RED.getRed());
        assertEquals(0, Color.RED.getGreen());
        assertEquals(0, Color.RED.getBlue());
        assertEquals(255, Color.RED.getAlpha());
    }
}
