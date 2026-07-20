package fr.lacaleche.glue.mcsx.core.mcsx;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Loads {@code .mcsx} documents by resource id. An id is {@code "namespace:path"} and maps to the
 * classpath resource {@code assets/<namespace>/ui/<path>.mcsx} — the same convention used by
 * {@code @UIController} and {@code <import from="…"/>}. Pure {@code java.*}: classpath I/O + the
 * parser, no Minecraft/ModernUI, so the mapping and loading are unit-testable headless.
 */
public final class DocumentLoader {

    private DocumentLoader() {
    }

    /** {@code "mcsx:demo"} → {@code "assets/mcsx/ui/demo.mcsx"}. */
    public static String resourcePath(String id) {
        int colon = id.indexOf(':');
        if (colon <= 0 || colon == id.length() - 1) {
            throw new IllegalArgumentException("document id must be 'namespace:path', got '" + id + "'");
        }
        return "assets/" + id.substring(0, colon) + "/ui/" + id.substring(colon + 1) + ".mcsx";
    }

    /** Loads and parses the document with the given id from {@code loader}'s classpath. */
    public static McsxDocument load(String id, ClassLoader loader) {
        String path = resourcePath(id);
        try (InputStream in = loader.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException(
                        "no .mcsx document '" + id + "' (looked for classpath resource " + path + ")");
            }
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return McsxParser.parseDocument(text);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read .mcsx document '" + id + "'", e);
        }
    }

    /** Loads from this class's own class loader (the common case for a mod's own assets). */
    public static McsxDocument loadFromClasspath(String id) {
        return load(id, DocumentLoader.class.getClassLoader());
    }
}
