package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The {@link GameTest} registry. Mods register their test definitions at client init; the one to
 * run is selected by launching with {@code -Dglue.gametest=<name>} (see {@link GameTestRunner}).
 */
@Environment(EnvType.CLIENT)
public final class GameTests {

    private static final Map<String, Supplier<GameTest>> TESTS = new LinkedHashMap<>();

    private GameTests() {
    }

    /**
     * Registers a test under {@code name}; the factory builds the test's step graph when the
     * runner selects it. This is the preferred form: steps routinely capture mutable state
     * (counters, futures, pre-built UI objects) in their closures, and building the graph per run
     * keeps that state fresh instead of freezing it at registration &mdash; it also means only the
     * selected test's graph is ever constructed. The built test's {@link GameTest#name()} must
     * equal {@code name}.
     */
    public static void register(String name, Supplier<GameTest> factory) {
        TESTS.put(name, factory);
    }

    /**
     * Registers an already-built test. Its steps' closure state is created here, once per game
     * launch &mdash; fine for a stateless step graph; a graph carrying mutable closure state
     * should use {@link #register(String, Supplier)} so a run never sees a previous run's state.
     */
    public static void register(GameTest test) {
        TESTS.put(test.name(), () -> test);
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

    /**
     * Builds the registered test {@code name} for one run, or returns {@code null} when the name
     * is unknown. Factory failures propagate; a factory whose product carries a different name
     * fails here, so a run is never reported under a name its graph does not match.
     */
    static GameTest build(String name) {
        Supplier<GameTest> factory = TESTS.get(name);
        if (factory == null) return null;

        GameTest test = factory.get();
        if (test == null || !name.equals(test.name())) {
            throw new IllegalStateException("factory registered as '" + name + "' built "
                    + (test == null ? "null" : "'" + test.name() + "'"));
        }
        return test;
    }

    static Set<String> names() {
        return TESTS.keySet();
    }
}
