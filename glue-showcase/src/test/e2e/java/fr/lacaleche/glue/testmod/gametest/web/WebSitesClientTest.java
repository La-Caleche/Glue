package fr.lacaleche.glue.testmod.gametest.web;

import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.web.browser.BrowserScreen;
import fr.lacaleche.glue.web.WebSurface;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Explicit network scenario; a site's challenge page is reported, never mistaken for search results. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@ClientTestSpec(explicitOnly = true)
public final class WebSitesClientTest extends WorldClientTest {

    @Override
    protected void test() {
        BrowserScreen browser = this.context.computeOnClient(client -> BrowserScreen.open(BrowserScreen.HOME));
        WebTestPage page = new WebTestPage(this.context, () -> browser.surface().orElse(null), () -> new Vector2i(0, BrowserScreen.PAGE_TOP));
        page.ready(false);
        @SuppressWarnings("PMD.CloseResource") // Borrowed from the browser screen; onClose owns disposal.
        WebSurface surface = this.context.computeOnClient(client -> browser.surface().orElseThrow());
        this.context.getInput().pressKey(GLFW.GLFW_KEY_F3);
        this.context.waitTicks(40);
        this.screenshot("lacaleche-chromium");
        this.context.runOnClient(client -> {
            Testmod.LOGGER.info("La Calèche delivery: {}", surface.metrics());
            surface.setFpsLimit(30);
        });
        this.context.waitTicks(100);
        this.context.runOnClient(client -> {
            Testmod.LOGGER.info("La Calèche cap30 delivery: {}", surface.metrics());
            surface.setFpsLimit(60);
        });
        BufferedImage image = this.game.await("browser-resolution capture", this.context.computeOnClient(client -> surface.screenshot()), Duration.ofSeconds(30));
        Path destination = this.context.computeOnClient(client -> client.gameDirectory.toPath().resolve("screenshots/native-lacaleche.png"));
        try {
            Files.createDirectories(destination.getParent());
            if (!ImageIO.write(image, "png", destination.toFile())) throw new IOException("No PNG writer");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }

        this.context.runOnClient(client -> browser.navigate("https://www.google.com/"));
        this.waitUntil("Google navigation", client -> surface.url().contains("google"));
        page.ready(false);
        this.screenshot("google-chromium");
        if (page.evaluate("!!document.getElementById('W0wltc')").getAsBoolean()) page.click("#W0wltc");
        page.click("textarea[name=q],input[name=q]");
        this.context.getInput().typeChars("chromium embedded framework");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        this.waitUntil("Google search response", client -> {
            String path = URI.create(surface.url()).getPath();
            return path.equals("/search") || path.startsWith("/sorry");
        });
        page.ready(false);
        this.context.runOnClient(client -> Testmod.LOGGER.info(URI.create(surface.url()).getPath().startsWith("/sorry")
                ? "Google returned a site challenge; search results are NOT validated."
                : "Google returned a search-results URL."));
        this.screenshot("google-search-native-input");
        this.context.runOnClient(client -> browser.navigate("https://www.youtube.com/"));
        this.waitUntil("YouTube navigation", client -> surface.url().contains("youtube"));
        page.ready(false);
        this.context.waitTicks(100);
        this.screenshot("youtube-chromium");
        this.context.runOnClient(client -> browser.onClose());
        page.disposed();
    }
}
