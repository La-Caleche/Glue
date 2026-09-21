package fr.lacaleche.glue.web.internal.options;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.internal.host.PageZoom;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/**
 * Glue Web's client settings on disk: the zoom a player chose for each page origin. A missing or
 * damaged file means no choice was made. So does a file written by 2.4, which held one scale for every
 * page; its keys are ignored and dropped on the next save.
 */
public final class SettingsFile {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");

    private SettingsFile() {
    }

    public static Map<String, Double> load() {
        return load(path());
    }

    public static void save(Map<String, Double> zoom) {
        save(path(), zoom);
    }

    static Map<String, Double> load(Path file) {
        if (!Files.isRegularFile(file)) return Map.of();
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonElement zoom = saved.get("zoom");
            if (zoom == null || !zoom.isJsonObject()) return Map.of();
            Map<String, Double> chosen = new TreeMap<>();
            for (Map.Entry<String, JsonElement> entry : zoom.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) continue;
                double factor = value.getAsDouble();
                if (Double.isFinite(factor) && factor >= PageZoom.MIN && factor <= PageZoom.MAX) {
                    chosen.put(entry.getKey(), factor);
                }
            }
            return Map.copyOf(chosen);
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unusable settings at {}, keeping the defaults: {}", file, exception.toString());
            return Map.of();
        }
    }

    static void save(Path file, Map<String, Double> zoom) {
        JsonObject chosen = new JsonObject();
        new TreeMap<>(zoom).forEach(chosen::addProperty);
        JsonObject saved = new JsonObject();
        saved.add("zoom", chosen);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not write the settings at {}", file, exception);
        }
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("glue-web.json");
    }
}
