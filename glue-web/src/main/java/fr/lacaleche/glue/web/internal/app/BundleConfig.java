package fr.lacaleche.glue.web.internal.app;

import fr.lacaleche.glue.web.app.WebApp;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;

import java.net.URI;
import java.nio.file.Path;
import java.security.PublicKey;
import java.security.interfaces.EdECPublicKey;
import java.util.Map;
import java.util.Objects;

/** Validated registration, independent of Fabric initialization and CEF. */
public record BundleConfig(String id, URI origin, Path embedded, String version, String contract,
                           URI channel, Map<String, PublicKey> keys, WebApp.Activation activation,
                           Path cache, boolean development) {

    public BundleConfig {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(embedded, "embedded");
        Objects.requireNonNull(cache, "cache");
        Objects.requireNonNull(activation, "activation");
        version(version);
        if (contract == null || !contract.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}")) {
            throw new IllegalArgumentException("Invalid application bridge contract: " + contract);
        }
        keys = Map.copyOf(keys);
        if (channel != null) {
            https(channel);
            if (keys.isEmpty()) throw new IllegalArgumentException("An update channel requires trusted Ed25519 keys");
        }
        for (Map.Entry<String, PublicKey> key : keys.entrySet()) {
            if (!key.getKey().matches("[A-Za-z0-9._-]{1,64}")
                    || !(key.getValue() instanceof EdECPublicKey ed)
                    || !ed.getParams().getName().equals("Ed25519")) {
                throw new IllegalArgumentException("Expected a named Ed25519 public key");
            }
        }
    }

    public static Version version(String value) {
        if (value == null || value.length() > 128 || !value.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?")) {
            throw new IllegalArgumentException("Expected a semantic version: " + value);
        }
        try {
            return SemanticVersion.parse(value);
        } catch (VersionParsingException exception) {
            throw new IllegalArgumentException("Invalid semantic version: " + value, exception);
        }
    }

    static void https(URI address) {
        if (!"https".equals(address.getScheme()) || address.getHost() == null || address.getUserInfo() != null
                || address.getFragment() != null) throw new IllegalArgumentException("Bundle URLs must use HTTPS without credentials or fragments");
    }
}
