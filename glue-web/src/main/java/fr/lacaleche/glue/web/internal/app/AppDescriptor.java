package fr.lacaleche.glue.web.internal.app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.IOException;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The application as its build declared it, read from {@code assets/<mod>/glue-web/apps/<name>.json}.
 * The Gradle plugin writes that file when it embeds a release, which is what keeps the version in the
 * jar and the version in this declaration from ever drifting apart.
 */
public record AppDescriptor(String contract, String version, String resources, URI channel,
                            Map<String, PublicKey> keys) {

    private static final int FORMAT = 1;

    public static String path(String modId, String name) {
        return "assets/" + modId + "/glue-web/apps/" + name + ".json";
    }

    /** Reads the file the build generated, or says which task writes it. */
    public static AppDescriptor read(String modId, String name) {
        String location = path(modId, name);
        ModContainer mod = FabricLoader.getInstance().getModContainer(modId)
                .orElseThrow(() -> new IllegalArgumentException("Mod is not loaded: " + modId));
        Path file = mod.findPath(location)
                .orElseThrow(() -> new IllegalStateException(modId + " ships no " + location
                        + "; the Glue bundle plugin writes it when it embeds a release"));
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8), modId, name);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + location, exception);
        }
    }

    /** Separate from the file lookup so the format can be exercised without a game. */
    public static AppDescriptor parse(String document, String modId, String name) {
        JsonObject object = JsonParser.parseString(document).getAsJsonObject();
        if (number(object, "format") != FORMAT) throw new IllegalArgumentException("Unsupported app descriptor format");
        String app = text(object, "app");
        if (!(modId + ":" + name).equals(app)) {
            throw new IllegalArgumentException("The descriptor describes " + app + ", not " + modId + ":" + name);
        }
        Map<String, PublicKey> keys = new LinkedHashMap<>();
        JsonElement declared = object.get("keys");
        if (declared == null || !declared.isJsonObject()) {
            throw new IllegalArgumentException("The descriptor of " + app + " declares no trusted keys");
        }
        for (Map.Entry<String, JsonElement> key : declared.getAsJsonObject().entrySet()) {
            keys.put(key.getKey(), publicKey(key.getKey(), key.getValue().getAsString()));
        }
        return new AppDescriptor(text(object, "contract"), text(object, "version"), text(object, "resources"),
                URI.create(text(object, "channel")), Map.copyOf(keys));
    }

    private static PublicKey publicKey(String keyId, String base64) {
        try {
            return KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64)));
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalArgumentException("The public key " + keyId + " is not Base64-encoded Ed25519 SPKI",
                    exception);
        }
    }

    private static String text(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("The app descriptor is missing a string for " + key);
        }
        return element.getAsString();
    }

    private static long number(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("The app descriptor is missing a whole number for " + key);
        }
        return element.getAsLong();
    }
}
