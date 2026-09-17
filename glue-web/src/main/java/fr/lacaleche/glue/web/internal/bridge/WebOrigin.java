package fr.lacaleche.glue.web.internal.bridge;

import fr.lacaleche.glue.web.internal.app.AppFiles;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;

/** Exact HTTP origin comparison, including normalized default ports. */
public record WebOrigin(String scheme, String host, int port) {

    public static WebOrigin from(URI uri) {
        Objects.requireNonNull(uri, "origin");
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if ((!scheme.equals("http") && !scheme.equals("https")) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getPort() > 65535) {
            throw new IllegalArgumentException("Web origins must be HTTP(S) URLs without credentials");
        }
        int port = uri.getPort() < 0 ? (scheme.equals("https") ? 443 : 80) : uri.getPort();
        return new WebOrigin(scheme, uri.getHost().toLowerCase(Locale.ROOT), port);
    }

    /** Glue app addresses are trusted by default; any other address needs an explicitly trusted origin. */
    public static WebOrigin defaultFor(URI address) {
        String host = address.getHost();
        if (!"https".equalsIgnoreCase(address.getScheme()) || host == null) return null;
        return host.toLowerCase(Locale.ROOT).endsWith(AppFiles.DOMAIN_SUFFIX) ? from(address) : null;
    }

    boolean matches(String address) {
        try {
            return this.equals(from(URI.create(address)));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
