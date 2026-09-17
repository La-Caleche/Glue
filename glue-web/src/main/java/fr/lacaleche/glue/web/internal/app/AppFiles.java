package fr.lacaleche.glue.web.internal.app;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Maps Glue app URLs to files below one web directory. Stateless and safe on any thread. */
public final class AppFiles {

    public static final String DOMAIN_SUFFIX = ".glue";
    private static final Pattern LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final String BINARY = "application/octet-stream";
    private static final Map<String, String> MIME_TYPES = Map.ofEntries(
            Map.entry("html", "text/html"),
            Map.entry("htm", "text/html"),
            Map.entry("js", "text/javascript"),
            Map.entry("mjs", "text/javascript"),
            Map.entry("css", "text/css"),
            Map.entry("json", "application/json"),
            Map.entry("map", "application/json"),
            Map.entry("txt", "text/plain"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"),
            Map.entry("webp", "image/webp"),
            Map.entry("avif", "image/avif"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"),
            Map.entry("ttf", "font/ttf"),
            Map.entry("otf", "font/otf"),
            Map.entry("wasm", "application/wasm"),
            Map.entry("mp3", "audio/mpeg"),
            Map.entry("ogg", "audio/ogg"),
            Map.entry("wav", "audio/wav"),
            Map.entry("mp4", "video/mp4"),
            Map.entry("webm", "video/webm"));

    private AppFiles() {
    }

    /**
     * The DNS host serving a mod. Underscores, valid in mod ids but not in host names, become a
     * double hyphen; ids that still do not form a host label are rejected.
     */
    public static String host(String modId) {
        String label = modId.replace("_", "--");
        if (!LABEL.matcher(label).matches()) {
            throw new IllegalArgumentException("Mod id cannot be served as a web host: " + modId);
        }
        return label + DOMAIN_SUFFIX;
    }

    static Response respond(Path root, String method, String url) throws IOException {
        if (!"GET".equalsIgnoreCase(method)) return Response.status(405);

        Path file;
        try {
            file = resolve(root, new URI(url).getPath());
        } catch (URISyntaxException exception) {
            return Response.status(400);
        }
        if (file == null) return Response.status(404);
        return new Response(200, mimeType(file.getFileName().toString()), Files.readAllBytes(file));
    }

    /** Returns the regular file for a decoded URL path, or null when it is missing or escapes the root. */
    static Path resolve(Path root, String path) {
        String target = path == null || path.isEmpty() ? "/" : path;
        if (target.charAt(0) != '/') return null;
        if (target.endsWith("/")) target += "index.html";

        Path file = root;
        try {
            for (String segment : target.substring(1).split("/", -1)) {
                if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                        || segment.indexOf('\\') >= 0 || segment.indexOf(':') >= 0 || segment.indexOf(0) >= 0) {
                    return null;
                }
                file = file.resolve(segment);
            }
        } catch (InvalidPathException exception) {
            return null;
        }
        if (Files.isDirectory(file)) file = file.resolve("index.html");
        return Files.isRegularFile(file) ? file : null;
    }

    static String mimeType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) return BINARY;
        return MIME_TYPES.getOrDefault(fileName.substring(dot + 1).toLowerCase(Locale.ROOT), BINARY);
    }

    static String reason(int status) {
        return switch (status) {
            case 200 -> "OK";
            case 400 -> "Bad Request";
            case 404 -> "Not Found";
            case 405 -> "Method Not Allowed";
            default -> "Internal Server Error";
        };
    }

    record Response(int status, String mimeType, byte[] body) {

        static Response status(int status) {
            return new Response(status, "text/plain", reason(status).getBytes(StandardCharsets.UTF_8));
        }
    }
}
