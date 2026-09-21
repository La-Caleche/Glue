package fr.lacaleche.glue.web.internal.options;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsFileTest {

    @Test
    void aMissingFileMeansNoChoice(@TempDir Path directory) {
        assertEquals(Map.of(), SettingsFile.load(directory.resolve("glue-web.json")));
    }

    @Test
    void savedChoicesComeBack(@TempDir Path directory) {
        Path file = directory.resolve("config/glue-web.json");
        Map<String, Double> chosen = Map.of("https://editor.ignis-editor.glue", 0.67, "http://localhost:5173", 0.5);
        SettingsFile.save(file, chosen);
        assertEquals(chosen, SettingsFile.load(file));
    }

    @Test
    void aFileFromTheGlobalScaleOf24MeansNoChoice(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("glue-web.json"), "{\"followGameScale\":false,\"scale\":1.5}");
        assertEquals(Map.of(), SettingsFile.load(file));
    }

    @Test
    void aChoiceOutsideTheRangeIsDropped(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("glue-web.json"),
                "{\"zoom\":{\"https://a.glue\":12,\"https://b.glue\":\"big\",\"https://c.glue\":0.75}}");
        assertEquals(Map.of("https://c.glue", 0.75), SettingsFile.load(file));
    }

    @Test
    void aDamagedFileMeansNoChoice(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("glue-web.json"), "{\"zoom\": }");
        assertEquals(Map.of(), SettingsFile.load(file));
    }
}
