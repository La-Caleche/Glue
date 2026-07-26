package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The {@link GameTool} registry. Mods register their tools at client init under a namespaced id
 * ({@code "ignis:open-editor"}); {@link GameTest#tool} resolves them lazily at step execution, so
 * no ordering between registering mods and test definitions is required.
 */
@Environment(EnvType.CLIENT)
public final class GameTools {

    private static final Map<String, GameTool> TOOLS = new LinkedHashMap<>();

    private GameTools() {
    }

    /** Registers {@code tool} under {@code id}; re-registering an id replaces the previous tool. */
    public static void register(String id, GameTool tool) {
        TOOLS.put(id, tool);
    }

    /** The tool registered under {@code id}, or an exception naming every registered id. */
    static GameTool require(String id) {
        GameTool tool = TOOLS.get(id);
        if (tool == null) {
            throw new IllegalArgumentException(
                    "no game tool '" + id + "' registered -- available: " + TOOLS.keySet());
        }
        return tool;
    }
}
