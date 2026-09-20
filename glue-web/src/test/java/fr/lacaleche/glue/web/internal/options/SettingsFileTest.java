package fr.lacaleche.glue.web.internal.options;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SettingsFileTest {

    @Test
    void aMissingFileFollowsTheGameScale(@TempDir Path directory) {
        assertEquals(new SettingsFile.Values(true, 2), SettingsFile.load(directory.resolve("glue-web.json")));
    }

    @Test
    void savedSettingsComeBack(@TempDir Path directory) {
        Path file = directory.resolve("config/glue-web.json");
        SettingsFile.save(file, new SettingsFile.Values(false, 1.25));
        assertEquals(new SettingsFile.Values(false, 1.25), SettingsFile.load(file));
    }

    @Test
    void anOutOfRangeScaleIsClamped(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("glue-web.json"), "{\"followGameScale\":false,\"scale\":12}");
        assertEquals(new SettingsFile.Values(false, 4), SettingsFile.load(file));
    }

    @Test
    void aDamagedFileKeepsTheDefaults(@TempDir Path directory) throws IOException {
        Path file = Files.writeString(directory.resolve("glue-web.json"), "{\"scale\": }");
        assertEquals(new SettingsFile.Values(true, 2), SettingsFile.load(file));
    }
}
