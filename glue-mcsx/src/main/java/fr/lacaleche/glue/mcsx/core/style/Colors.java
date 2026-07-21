package fr.lacaleche.glue.mcsx.core.style;

/**
 * Hex color parsing shared by the Tailwind arbitrary-value path ({@code bg-[#hex]}) and the binder's
 * raw box attributes ({@code bg="#hex"}, {@code color="#hex"}). Accepts {@code #rgb}, {@code #rrggbb}
 * and {@code #rrggbbaa} (web order); returns a packed ARGB int (alpha in the high byte).
 */
public final class Colors {

    private Colors() {
    }

    public static int parseHex(String hex, String context) {
        if (!hex.startsWith("#")) {
            throw new TailwindException("color in '" + context + "' must start with '#'");
        }
        String h = hex.substring(1);
        int r;
        int g;
        int b;
        int a = 0xFF;
        try {
            switch (h.length()) {
                case 3 -> {
                    r = pair(h.charAt(0), h.charAt(0));
                    g = pair(h.charAt(1), h.charAt(1));
                    b = pair(h.charAt(2), h.charAt(2));
                }
                case 6 -> {
                    r = pair(h.charAt(0), h.charAt(1));
                    g = pair(h.charAt(2), h.charAt(3));
                    b = pair(h.charAt(4), h.charAt(5));
                }
                case 8 -> {
                    r = pair(h.charAt(0), h.charAt(1));
                    g = pair(h.charAt(2), h.charAt(3));
                    b = pair(h.charAt(4), h.charAt(5));
                    a = pair(h.charAt(6), h.charAt(7));
                }
                default -> throw new TailwindException(
                        "invalid hex color in '" + context + "' (expected #rgb, #rrggbb or #rrggbbaa)");
            }
        } catch (NumberFormatException e) {
            throw new TailwindException("invalid hex color in '" + context + "'");
        }
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int pair(char hi, char lo) {
        int h = Character.digit(hi, 16);
        int l = Character.digit(lo, 16);
        if (h < 0 || l < 0) {
            throw new NumberFormatException();
        }
        return (h << 4 | l) & 0xFF;
    }
}
