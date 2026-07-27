package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The {@link GameTest} registry. Mods register their test definitions at client init; the one to
 * run is selected by launching with {@code -Dglue.gametest=<name>} (see {@link GameTestRunner}).
 */
@Environment(EnvType.CLIENT)
public final class GameTests {

    private static final Map<String, GameTest> TESTS = new LinkedHashMap<>();

    private GameTests() {
    }

    public static void register(GameTest test) {
        TESTS.put(test.name(), test);
    }

    /**
     * Whether this launch runs a scripted test ({@code -Dglue.gametest} is set). Mod UI may consult
     * this to skip prompts that would block an unattended run (crash-recovery dialogs, first-run
     * wizards) — skip, not auto-answer: a scripted run must never consume or destroy state a human
     * session would be asked about.
     */
    public static boolean armed() {
        String name = System.getProperty("glue.gametest");
        return name != null && !name.isEmpty();
    }

    static GameTest get(String name) {
        return TESTS.get(name);
    }

    static Set<String> names() {
        return TESTS.keySet();
    }
}
