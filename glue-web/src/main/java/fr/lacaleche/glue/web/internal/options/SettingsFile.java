package fr.lacaleche.glue.web.internal.options;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.WebSettings;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Glue Web's client settings on disk; a missing or damaged file falls back to the defaults. */
public final class SettingsFile {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");
    private static final Values DEFAULTS = new Values(true, 2);

    private SettingsFile() {
    }

    public static Values load() {
        return load(path());
    }

    public static void save(Values values) {
        save(path(), values);
    }

    static Values load(Path file) {
        if (!Files.isRegularFile(file)) return DEFAULTS;
        try {
            JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            boolean followsGameScale = !saved.has("followGameScale") || saved.get("followGameScale").getAsBoolean();
            double scale = saved.has("scale") ? saved.get("scale").getAsDouble() : DEFAULTS.scale();
            return new Values(followsGameScale, clamp(scale));
        } catch (IOException | RuntimeException exception) {
            LOGGER.warn("Unusable settings at {}, keeping the defaults: {}", file, exception.toString());
            return DEFAULTS;
        }
    }

    static void save(Path file, Values values) {
        JsonObject saved = new JsonObject();
        saved.addProperty("followGameScale", values.followsGameScale());
        saved.addProperty("scale", values.scale());
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(saved), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.warn("Could not write the settings at {}", file, exception);
        }
    }

    private static double clamp(double scale) {
        if (!Double.isFinite(scale)) return DEFAULTS.scale();
        return Math.clamp(scale, WebSettings.MIN_SCALE, WebSettings.MAX_SCALE);
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("glue-web.json");
    }

    /** The persisted settings; the scale is remembered while pages follow the GUI scale. */
    public record Values(boolean followsGameScale, double scale) {
    }
}
