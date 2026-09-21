package fr.lacaleche.glue.web.internal.host;

import fr.lacaleche.glue.web.internal.options.SettingsFile;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * How large a page is drawn, as a factor of Minecraft's GUI scale. At 1 a CSS pixel is a GUI pixel,
 * which is how vanilla screens are drawn and how a page designed for the game fits it; a page designed
 * at desktop density declares less. The player changes it per page with a browser's own keys, and the
 * choice is kept per origin, the way a browser keeps it per site.
 *
 * <p>The zoom multiplies the GUI scale rather than replacing it, so a page keeps following the game:
 * a player who raises the GUI scale enlarges every page with it, whatever each page declared.</p>
 */
public final class PageZoom {

    public static final double MIN = 0.25;
    public static final double MAX = 4;

    /** The steps Chromium offers, so the keys behave as they do in a browser. */
    private static final double[] STEPS = {0.25, 0.33, 0.5, 0.67, 0.75, 0.8, 0.9, 1, 1.1, 1.25, 1.5, 1.75, 2, 2.5, 3, 4};
    private static final double EPSILON = 1e-6;

    private static Map<String, Double> saved;

    private PageZoom() {
    }

    /** @throws IllegalArgumentException when the zoom is not finite or leaves {@link #MIN}..{@link #MAX} */
    public static double validate(double zoom) {
        if (!Double.isFinite(zoom) || zoom < MIN || zoom > MAX) {
            throw new IllegalArgumentException("Page zoom must be in " + MIN + ".." + MAX + ": " + zoom);
        }
        return zoom;
    }

    /** The zoom a page opens at: the player's choice for its origin, or what its host declared. */
    public static double initial(URI address, double declared) {
        Double chosen = saved().get(origin(address));
        return chosen == null ? declared : chosen;
    }

    /**
     * Applies a zoom key to a page: returns its new zoom and remembers it for the page's origin, or is
     * empty when the key is not a zoom key. The accelerator is Control, or Command on macOS, as in a
     * browser. Resetting forgets the choice, so the page returns to what its host declared.
     */
    public static OptionalDouble handle(URI address, double declared, double current, int key, int scanCode,
                                        int modifiers) {
        int accelerator = Minecraft.ON_OSX ? GLFW.GLFW_MOD_SUPER : GLFW.GLFW_MOD_CONTROL;
        if ((modifiers & accelerator) == 0) return OptionalDouble.empty();
        Command command = command(key, GLFW.glfwGetKeyName(key, scanCode));
        if (command == null) return OptionalDouble.empty();

        String origin = origin(address);
        Map<String, Double> updated = new TreeMap<>(saved());
        double zoom;
        if (command == Command.RESET) {
            updated.remove(origin);
            zoom = declared;
        } else {
            zoom = step(current, command == Command.IN);
            updated.put(origin, zoom);
        }
        store(updated);
        return OptionalDouble.of(zoom);
    }

    /**
     * Which command a key is. The physical keys cover QWERTY and the numeric keypad; the key's name on
     * the active layout covers the rest — on AZERTY, minus sits on the 6 key.
     */
    static Command command(int key, String name) {
        return switch (key) {
            case GLFW.GLFW_KEY_EQUAL, GLFW.GLFW_KEY_KP_ADD -> Command.IN;
            case GLFW.GLFW_KEY_MINUS, GLFW.GLFW_KEY_KP_SUBTRACT -> Command.OUT;
            case GLFW.GLFW_KEY_0, GLFW.GLFW_KEY_KP_0 -> Command.RESET;
            default -> name == null ? null : switch (name) {
                case "+", "=" -> Command.IN;
                case "-" -> Command.OUT;
                default -> null;
            };
        };
    }

    /** The next step in that direction, from any value — a declared zoom need not be a step. */
    static double step(double current, boolean in) {
        if (in) {
            for (double step : STEPS) {
                if (step > current + EPSILON) return step;
            }
            return MAX;
        }
        for (int i = STEPS.length - 1; i >= 0; i--) {
            if (STEPS[i] < current - EPSILON) return STEPS[i];
        }
        return MIN;
    }

    /** What a choice is kept under: scheme, host and port, as a browser keys its zoom by site. */
    static String origin(URI address) {
        String origin = address.getScheme().toLowerCase(Locale.ROOT) + "://" + address.getHost().toLowerCase(Locale.ROOT);
        return address.getPort() < 0 ? origin : origin + ":" + address.getPort();
    }

    private static Map<String, Double> saved() {
        if (saved == null) saved = SettingsFile.load();
        return saved;
    }

    private static void store(Map<String, Double> updated) {
        if (updated.equals(saved())) return;
        saved = Map.copyOf(updated);
        SettingsFile.save(updated);
    }

    enum Command { IN, OUT, RESET }
}
