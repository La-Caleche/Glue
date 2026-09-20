package fr.lacaleche.glue.web.app;

import fr.lacaleche.glue.web.internal.app.AppResources;
import fr.lacaleche.glue.web.internal.app.AppDescriptor;
import fr.lacaleche.glue.web.internal.app.AppFiles;
import fr.lacaleche.glue.web.internal.app.BundleApp;
import fr.lacaleche.glue.web.internal.app.BundleConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PublicKey;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A mod's local web pages. Files under {@code assets/<mod>/web/} are served from
 * {@code https://<mod>.glue/} inside Glue surfaces, with no network server. Each mod has its own
 * origin, storage and bridge trust.
 *
 * <p>During development, {@code -Dglue.web.source.<mod>=<directory>} serves a directory instead, so
 * edited files only need a page reload.</p>
 */
public final class WebApp {

    private final String modId;
    private final URI origin;
    private final BundleApp bundles;

    private WebApp(String modId) {
        this.modId = modId;
        this.origin = AppResources.origin(modId);
        this.bundles = null;
    }

    private WebApp(String modId, BundleApp bundles) {
        this.modId = modId;
        this.origin = bundles.config().origin();
        this.bundles = bundles;
    }

    /** Registers a named, versioned application through {@link Builder#register()}. */
    public static Builder builder(String modId, String name) {
        return new Builder(modId, name);
    }

    /**
     * The application as the build declared it: contract, update channel, trusted keys and the version
     * actually embedded, read from the descriptor the Glue bundle Gradle plugin generates. Nothing the
     * build already knows is repeated here, so the embedded version cannot drift from the files beside
     * it — the drift a channel resolves against, and would resolve wrongly.
     *
     * <p>Set what the build does not know, such as the activation policy, then {@link Builder#register()}.</p>
     *
     * @throws IllegalStateException when the mod ships no descriptor for that application
     */
    public static Builder declared(String modId, String name) {
        AppDescriptor descriptor = AppDescriptor.read(Objects.requireNonNull(modId, "modId"),
                Objects.requireNonNull(name, "name"));
        return builder(modId, name)
                .embedded(descriptor.version(), descriptor.resources())
                .contract(descriptor.contract())
                .updates(descriptor.channel(), descriptor.keys());
    }

    /**
     * @throws IllegalArgumentException when the mod is not loaded, ships no web directory, or has an id
     *                                  that cannot be a host name
     */
    public static WebApp of(String modId) {
        Objects.requireNonNull(modId, "modId");
        if (!FabricLoader.getInstance().isModLoaded(modId)) throw new IllegalArgumentException("Mod is not loaded: " + modId);
        WebApp app = new WebApp(modId);
        if (AppResources.root(modId).isEmpty()) {
            throw new IllegalArgumentException("Mod " + modId + " has no assets/" + modId + "/web directory");
        }
        return app;
    }

    public String modId() {
        return this.modId;
    }

    /** The app origin, ending with a slash. */
    public URI origin() {
        return this.origin;
    }

    /** A page or file of this app, relative to its web directory; a query or fragment may follow. */
    public URI page(String path) {
        Objects.requireNonNull(path, "path");
        String relative = path.startsWith("/") ? path.substring(1) : path;
        URI address = this.origin.resolve(relative);
        if (!this.origin.getHost().equals(address.getHost()) || !"https".equals(address.getScheme())) {
            throw new IllegalArgumentException("Page path must stay inside " + this.origin + ": " + path);
        }
        return address;
    }

    /** Snapshot for a builder-registered app; callable from any thread. */
    public WebAppStatus status() {
        return this.managed().status();
    }

    /** Notifications run on the Minecraft client thread. Run the returned function to unsubscribe. */
    public Runnable onStatus(Consumer<WebAppStatus> listener) {
        return this.managed().observe(Objects.requireNonNull(listener, "listener"));
    }

    /** Shared background operation; failure is reported in the returned snapshot, keeping the local app usable. */
    public CompletableFuture<WebAppStatus> checkForUpdates() {
        return this.managed().check();
    }

    /** Selects the prepared update for future surfaces. Already-open surfaces retain their release. */
    public CompletableFuture<WebAppStatus> activateUpdate() {
        return this.managed().activate();
    }

    /** Persists an embedded-only selection until resumeUpdates or an explicit activation. */
    public CompletableFuture<WebAppStatus> useEmbedded() {
        return this.managed().useEmbedded();
    }

    /** Pins the previously selected release, including across restarts. */
    public CompletableFuture<WebAppStatus> usePrevious() {
        return this.managed().usePrevious();
    }

    /** Restores the configured activation policy for future surfaces. */
    public CompletableFuture<WebAppStatus> resumeUpdates() {
        return this.managed().resumeUpdates();
    }

    private BundleApp managed() {
        if (this.bundles == null) throw new IllegalStateException("Use WebApp.builder to register a versioned application");
        return this.bundles;
    }

    public enum Activation { NEXT_OPEN, MANUAL }

    public static final class Builder {

        private final String modId;
        private final String name;
        private String resources = "web";
        private String version;
        private String contract;
        private URI channel;
        private Map<String, PublicKey> keys = Map.of();
        private Activation activation = Activation.NEXT_OPEN;

        private Builder(String modId, String name) {
            AppFiles.host(Objects.requireNonNull(modId, "modId"));
            AppFiles.host(Objects.requireNonNull(name, "name"));
            this.modId = modId;
            this.name = name;
        }

        /** Version and directory relative to assets/modId, containing index.html. */
        public Builder embedded(String version, String resources) {
            BundleConfig.version(version);
            if (resources == null || !resources.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*")) {
                throw new IllegalArgumentException("Expected a directory relative to the mod's assets");
            }
            this.version = version;
            this.resources = resources;
            return this;
        }

        /** Application-owned bridge contract, matched exactly against the signed channel. */
        public Builder contract(String contract) {
            this.contract = contract;
            return this;
        }

        /** Optional HTTPS channel and accepted Ed25519 public keys, keyed by signing-key ID. */
        public Builder updates(URI channel, Map<String, PublicKey> keys) {
            this.channel = Objects.requireNonNull(channel, "channel");
            this.keys = Map.copyOf(keys);
            return this;
        }

        public Builder activation(Activation activation) {
            this.activation = Objects.requireNonNull(activation, "activation");
            return this;
        }

        /**
         * Register once during client initialization. Embedded pages are immediately available; cached
         * release validation and the initial update check run in the background. Glue owns shutdown.
         * The named development override glue.web.source.modId.name disables remote updates.
         */
        public WebApp register() {
            Minecraft client = Minecraft.getInstance();
            ModContainer mod = FabricLoader.getInstance().getModContainer(this.modId)
                    .orElseThrow(() -> new IllegalArgumentException("Mod is not loaded: " + this.modId));
            String override = System.getProperty(AppResources.SOURCE_PROPERTY + this.modId + "." + this.name);
            Path root = override == null ? mod.findPath("assets/" + this.modId + "/" + this.resources)
                    .orElseThrow(() -> new IllegalArgumentException("Embedded web directory is missing")) : Path.of(override);
            if (!Files.isRegularFile(root.resolve("index.html"))) throw new IllegalArgumentException("Embedded web directory must contain index.html");
            String label = AppFiles.host(this.name).replaceFirst("\\.glue$", "");
            URI origin = URI.create("https://" + label + "." + AppFiles.host(this.modId) + "/");
            Path cache = FabricLoader.getInstance().getGameDir().resolve("glue-web/apps").resolve(this.modId).resolve(this.name);
            BundleConfig config = new BundleConfig(this.modId + ":" + this.name, origin, root, this.version,
                    this.contract, this.channel, this.keys, this.activation, cache, override != null);
            BundleApp app = AppResources.add(config, client);
            return new WebApp(this.modId, app);
        }
    }
}
