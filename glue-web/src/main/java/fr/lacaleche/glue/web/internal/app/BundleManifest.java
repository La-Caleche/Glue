package fr.lacaleche.glue.web.internal.app;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/** The signature covers the exact decoded payload bytes, without JSON canonicalization. */
record BundleManifest(long revision, Release release, byte[] envelope) {

    static final int MAX_MANIFEST = 256 * 1024;
    static final int MAX_ARCHIVE = 64 * 1024 * 1024;

    static BundleManifest verify(byte[] bytes, BundleConfig config) throws IOException {
        if (bytes.length > MAX_MANIFEST) throw new IOException("Bundle manifest exceeds 256 KiB");
        try {
            JsonObject envelope = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (number(envelope, "format") != 1) throw new IOException("Unsupported signature envelope format");
            PublicKey key = config.keys().get(text(envelope, "keyId"));
            if (key == null) throw new IOException("Untrusted bundle signing key");
            byte[] payload = Base64.getDecoder().decode(text(envelope, "payload"));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(payload);
            if (!verifier.verify(Base64.getDecoder().decode(text(envelope, "signature")))) {
                throw new IOException("Invalid bundle signature");
            }
            JsonObject manifest = JsonParser.parseString(new String(payload, StandardCharsets.UTF_8)).getAsJsonObject();
            if (number(manifest, "format") != 1) throw new IOException("Unsupported bundle manifest format");
            if (!config.id().equals(text(manifest, "app"))
                    || !config.channel().toASCIIString().equals(text(manifest, "channel"))) {
                throw new IOException("Bundle manifest belongs to another application or channel");
            }
            long revision = number(manifest, "revision");
            if (revision < 1) throw new IOException("Channel revision must be positive");
            Set<String> contracts = new HashSet<>();
            Release selected = null;
            for (JsonElement element : manifest.getAsJsonArray("releases")) {
                JsonObject entry = element.getAsJsonObject();
                String contract = text(entry, "contract");
                if (!contracts.add(contract)) throw new IOException("Channel contains duplicate contracts");
                String version = text(entry, "version");
                BundleConfig.version(version);
                URI archive = URI.create(text(entry, "url"));
                BundleConfig.https(archive);
                long size = number(entry, "size");
                String digest = text(entry, "sha256");
                if (size < 1 || size > MAX_ARCHIVE || !digest.matches("[a-f0-9]{64}")) {
                    throw new IOException("Invalid bundle size or SHA-256");
                }
                if (config.contract().equals(contract) && BundleConfig.version(version).compareTo(BundleConfig.version(config.version())) >= 0) {
                    selected = new Release(version, archive, (int) size, digest);
                }
            }
            return new BundleManifest(revision, selected, bytes.clone());
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IOException("Invalid signed bundle manifest", exception);
        }
    }

    private static String text(JsonObject object, String key) {
        if (!object.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException("Expected a string: " + key);
        return object.get(key).getAsString();
    }

    private static long number(JsonObject object, String key) {
        if (!object.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException("Expected an integer: " + key);
        return object.get(key).getAsBigDecimal().longValueExact();
    }

    record Release(String version, URI url, int size, String sha256) { }
}
