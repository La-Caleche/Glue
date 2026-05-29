package fr.lacaleche.glue.math;

public final class Color {

    public static final Color BLACK = ofRGBA(0, 0, 0, 255);
    public static final Color WHITE = ofRGBA(255, 255, 255, 255);
    public static final Color RED = ofRGBA(255, 0, 0, 255);

    private final int color;

    private Color(int color) {
        this.color = color;
    }

    public static Color ofTransparent(int color) {
        return new Color(color);
    }

    public static Color ofOpaque(int color) {
        return new Color(0xFF000000 | color);
    }

    public static Color ofRGB(float r, float g, float b) {
        return ofRGBA(r, g, b, 1f);
    }

    public static Color ofRGB(int r, int g, int b) {
        return ofRGBA(r, g, b, 255);
    }

    public static Color ofRGBA(float r, float g, float b, float a) {
        return ofRGBA(
                (int) (r * 255 + 0.5),
                (int) (g * 255 + 0.5),
                (int) (b * 255 + 0.5),
                (int) (a * 255 + 0.5)
        );
    }

    public static Color ofRGBA(int r, int g, int b, int a) {
        return new Color(
                ((a & 0xFF) << 24) |
                        ((r & 0xFF) << 16) |
                        ((g & 0xFF) << 8) |
                        (b & 0xFF)
        );
    }

    public static Color ofHSB(float hue, float saturation, float brightness) {
        return ofOpaque(hsbToRgb(hue, saturation, brightness));
    }

    public static int hsbToRgb(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float hueNormalized = (hue - (float) Math.floor(hue)) * 6.0f;
            float fractionalPart = hueNormalized - (float) Math.floor(hueNormalized);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * fractionalPart);
            float t = brightness * (1.0f - (saturation * (1.0f - fractionalPart)));
            switch ((int) hueNormalized) {
                case 0:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (t * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 1:
                    r = (int) (q * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (p * 255.0f + 0.5f);
                    break;
                case 2:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (brightness * 255.0f + 0.5f);
                    b = (int) (t * 255.0f + 0.5f);
                    break;
                case 3:
                    r = (int) (p * 255.0f + 0.5f);
                    g = (int) (q * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 4:
                    r = (int) (t * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (brightness * 255.0f + 0.5f);
                    break;
                case 5:
                    r = (int) (brightness * 255.0f + 0.5f);
                    g = (int) (p * 255.0f + 0.5f);
                    b = (int) (q * 255.0f + 0.5f);
                    break;
                default:
                    throw new AssertionError("Unexpected HSB sector: " + (int) hueNormalized);
            }
        }
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public int getColor() {
        return color;
    }

    public int getAlpha() {
        return color >> 24 & 0xFF;
    }

    public int getRed() {
        return color >> 16 & 0xFF;
    }

    public int getGreen() {
        return color >> 8 & 0xFF;
    }

    public int getBlue() {
        return color & 0xFF;
    }

    public Color brighter(double factor) {
        if (factor <= 1.0) throw new IllegalArgumentException("factor must be > 1.0, got: " + factor);
        int r = getRed(), g = getGreen(), b = getBlue();
        int minChannelThreshold = (int) (1.0 / (1.0 - (1.0 / factor)));
        if (r == 0 && g == 0 && b == 0) {
            return ofRGBA(minChannelThreshold, minChannelThreshold, minChannelThreshold, getAlpha());
        }
        if (r > 0 && r < minChannelThreshold) r = minChannelThreshold;
        if (g > 0 && g < minChannelThreshold) g = minChannelThreshold;
        if (b > 0 && b < minChannelThreshold) b = minChannelThreshold;
        return ofRGBA(Math.min((int) (r / (1 / factor)), 255),
                Math.min((int) (g / (1 / factor)), 255),
                Math.min((int) (b / (1 / factor)), 255),
                getAlpha());
    }

    public Color darker(double factor) {
        if (factor <= 1.0) throw new IllegalArgumentException("factor must be > 1.0, got: " + factor);
        return ofRGBA(Math.max((int) (getRed() * (1.0 / factor)), 0),
                Math.max((int) (getGreen() * (1.0 / factor)), 0),
                Math.max((int) (getBlue() * (1.0 / factor)), 0),
                getAlpha());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        return color == ((Color) other).color;
    }

    @Override
    public int hashCode() {
        return color;
    }

    @Override
    public String toString() {
        return String.format("#%08X", color);
    }
}