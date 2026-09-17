package fr.lacaleche.glue.web.internal.app;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AppFilesTest {

    @TempDir
    Path directory;
    private Path root;

    @BeforeEach
    void createRoot() throws IOException {
        this.root = Files.createDirectory(this.directory.resolve("web"));
    }

    @Test
    void mapsModIdsToHostLabels() {
        assertEquals("glue-showcase.glue", AppFiles.host("glue-showcase"));
        assertEquals("my--mod.glue", AppFiles.host("my_mod"));
        assertThrows(IllegalArgumentException.class, () -> AppFiles.host("trailing_"));
        assertThrows(IllegalArgumentException.class, () -> AppFiles.host("Upper"));
        assertThrows(IllegalArgumentException.class, () -> AppFiles.host("a".repeat(64)));
    }

    @Test
    void servesFilesAndDirectoryIndexes() throws IOException {
        write("index.html", "<h1>root</h1>");
        write("hud/index.html", "<h1>hud</h1>");
        write("hud/app.js", "export {}");

        assertBody("<h1>root</h1>", "https://demo.glue/");
        assertBody("<h1>hud</h1>", "https://demo.glue/hud");
        assertBody("<h1>hud</h1>", "https://demo.glue/hud/");
        AppFiles.Response script = AppFiles.respond(this.root, "GET", "https://demo.glue/hud/app.js?v=2#top");
        assertEquals(200, script.status());
        assertEquals("text/javascript", script.mimeType());
    }

    @Test
    void decodesPathsButNeverLeavesTheRoot() throws IOException {
        write("with space.css", "body{}");
        Files.writeString(this.root.resolveSibling("secret.txt"), "secret", StandardCharsets.UTF_8);

        assertBody("body{}", "https://demo.glue/with%20space.css");
        for (String url : new String[] {"https://demo.glue/../secret.txt", "https://demo.glue/%2e%2e/secret.txt",
                "https://demo.glue/a/..%2F..%2Fsecret.txt", "https://demo.glue/C:%5Csecret.txt",
                "https://demo.glue//secret.txt", "https://demo.glue/missing.html"}) {
            assertEquals(404, AppFiles.respond(this.root, "GET", url).status(), url);
        }
        assertNull(AppFiles.resolve(this.root, "relative.css"));
    }

    @Test
    void rejectsUnsupportedMethodsAndMalformedUrls() throws IOException {
        write("index.html", "ok");
        assertEquals(405, AppFiles.respond(this.root, "POST", "https://demo.glue/").status());
        assertEquals(400, AppFiles.respond(this.root, "GET", "https://demo.glue/a b").status());
    }

    @Test
    void choosesContentTypesByExtension() {
        assertEquals("text/html", AppFiles.mimeType("PAGE.HTML"));
        assertEquals("text/css", AppFiles.mimeType("style.css"));
        assertEquals("image/svg+xml", AppFiles.mimeType("icon.svg"));
        assertEquals("font/woff2", AppFiles.mimeType("font.woff2"));
        assertEquals("application/octet-stream", AppFiles.mimeType("archive"));
        assertEquals("application/octet-stream", AppFiles.mimeType("data.bin"));
    }

    private void write(String path, String content) throws IOException {
        Path file = this.root.resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private void assertBody(String expected, String url) throws IOException {
        AppFiles.Response response = AppFiles.respond(this.root, "GET", url);
        assertEquals(200, response.status(), url);
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8), response.body(), url);
    }
}
