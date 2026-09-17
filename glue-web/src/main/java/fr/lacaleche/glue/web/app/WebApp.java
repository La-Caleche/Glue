package fr.lacaleche.glue.web.app;

import fr.lacaleche.glue.web.internal.app.AppResources;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.util.Objects;

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

    private WebApp(String modId) {
        this.modId = modId;
        this.origin = AppResources.origin(modId);
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
}
