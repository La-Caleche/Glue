package fr.lacaleche.glue.testmod.web;

import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.app.WebAppStatus;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.host.WebScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/** Runs offline by default; an optional signed HTTPS channel exercises the same producer protocol as a mod. */
public final class BundleDemo {

    private static WebApp app;

    private BundleDemo() {
    }

    public static void init() {
        WebApp.Builder builder = WebApp.builder(WebDemos.MOD_ID, "bundles")
                .embedded("1.0.0", "web-bundles").contract("showcase.bundles/1").activation(WebApp.Activation.MANUAL);
        UpdateChannel updates = readUpdateChannel(FabricLoader.getInstance().getConfigDir().resolve("glue-showcase/bundles.properties"));
        if (updates != null) builder.updates(updates.url(), Map.of("release", updates.publicKey()));
        app = builder.register();
    }

    public static WebApp app() {
        return app;
    }

    public static WebScreen open() {
        return WebScreen.builder(app.page("index.html")).title(Component.literal("Versioned web bundles"))
                .bind(new Actions()).state("bundle", app::status).open();
    }

    /** An absent configuration keeps the demo offline; an incomplete/invalid one fails explicitly. */
    static UpdateChannel readUpdateChannel(Path file) {
        if (!Files.exists(file)) return null;
        Properties configuration = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            configuration.load(reader);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read bundle demo configuration " + file, exception);
        }
        String channel = configuration.getProperty("channel");
        String key = configuration.getProperty("publicKey");
        if (channel == null || channel.isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("Expected channel and publicKey in " + file);
        }
        URI url = URI.create(channel);
        if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null) {
            throw new IllegalArgumentException("The bundle demo channel must be an HTTPS URL in " + file);
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)));
            return new UpdateChannel(url, publicKey);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Invalid bundle demo public key in " + file, exception);
        }
    }

    record UpdateChannel(URI url, PublicKey publicKey) {
    }

    private static final class Actions {

        @WebAction("bundle.check")
        CompletableFuture<WebAppStatus> check() {
            return app.checkForUpdates();
        }

        @WebAction("bundle.activate")
        CompletableFuture<WebAppStatus> activate() {
            return app.activateUpdate();
        }

        @WebAction("bundle.embedded")
        CompletableFuture<WebAppStatus> embedded() {
            return app.useEmbedded();
        }

        @WebAction("bundle.previous")
        CompletableFuture<WebAppStatus> previous() {
            return app.usePrevious();
        }

        @WebAction("bundle.resume")
        CompletableFuture<WebAppStatus> resume() {
            return app.resumeUpdates();
        }
    }
}
