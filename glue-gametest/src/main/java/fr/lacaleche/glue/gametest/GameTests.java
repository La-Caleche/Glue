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

    static GameTest get(String name) {
        return TESTS.get(name);
    }

    static Set<String> names() {
        return TESTS.keySet();
    }
}
