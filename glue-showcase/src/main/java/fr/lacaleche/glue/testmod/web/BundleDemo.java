package fr.lacaleche.glue.testmod.web;

import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.app.WebAppStatus;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Runs offline by default; an optional signed HTTPS channel exercises the same producer protocol as a mod. */
public final class BundleDemo {

    private static WebApp app;

    private BundleDemo() {
    }

    public static void init() {
        WebApp.Builder builder = WebApp.builder(WebDemos.MOD_ID, "bundles")
                .embedded("1.0.0", "web-bundles").contract("showcase.bundles/1").activation(WebApp.Activation.MANUAL);
        String channel = System.getProperty("glue.showcase.web.channel");
        String key = System.getProperty("glue.showcase.web.key");
        if (channel != null || key != null) {
            if (channel == null || key == null) throw new IllegalArgumentException("Set both glue.showcase.web.channel and glue.showcase.web.key");
            try {
                builder.updates(URI.create(channel), Map.of("release", KeyFactory.getInstance("Ed25519")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(key)))));
            } catch (GeneralSecurityException exception) {
                throw new IllegalArgumentException("Invalid showcase bundle public key", exception);
            }
        }
        app = builder.register();
    }

    public static WebApp app() {
        return app;
    }

    public static WebScreen open() {
        return WebScreen.builder(app.page("index.html")).title(Component.literal("Versioned web bundles"))
                .bind(new Actions()).state("bundle", app::status).open();
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
