package fr.lacaleche.glue.web.internal.app;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** One writer per application cache, including across Minecraft processes. Used only by its app worker. */
final class BundleStore implements AutoCloseable {

    private final BundleConfig config;
    private FileChannel lockChannel;
    private FileLock lock;

    BundleStore(BundleConfig config) {
        this.config = config;
    }

    void open() throws IOException {
        Files.createDirectories(this.config.cache());
        if (this.lock != null && this.lock.isValid()) return;
        this.lockChannel = FileChannel.open(this.config.cache().resolve("lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            this.lock = this.lockChannel.tryLock();
            if (this.lock == null) throw new IOException("Application cache is already in use by another client");
        } catch (OverlappingFileLockException exception) {
            this.lockChannel.close();
            throw new IOException("Application cache is already in use", exception);
        } catch (IOException exception) {
            this.lockChannel.close();
            throw exception;
        }
        Files.createDirectories(this.config.cache().resolve("releases"));
        try (Stream<Path> paths = Files.list(this.config.cache())) {
            for (Path path : paths.filter(path -> path.getFileName().toString().startsWith("stage-")).toList()) delete(path);
        }
    }

    byte[] read(String name, int maximum) throws IOException {
        Path path = this.config.cache().resolve(name);
        if (!Files.exists(path)) return null;
        if (Files.size(path) > maximum) throw new IOException("Cached " + name + " exceeds its size limit");
        return Files.readAllBytes(path);
    }

    void write(String name, byte[] bytes) throws IOException {
        Path temporary = Files.createTempFile(this.config.cache(), "stage-", ".tmp");
        try {
            Files.write(temporary, bytes);
            Files.move(temporary, this.config.cache().resolve(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Path restore(String token) throws IOException {
        checkToken(token);
        byte[] envelope = this.read("releases/" + token + "/manifest.json", BundleManifest.MAX_MANIFEST);
        if (envelope == null) throw new IOException("Cached bundle manifest is missing");
        BundleManifest manifest = BundleManifest.verify(envelope, this.config);
        if (manifest.release() == null || !token(manifest.release()).equals(token)) {
            throw new IOException("Cached bundle is incompatible with this application");
        }
        byte[] archive = this.read("releases/" + token + "/bundle.zip", BundleManifest.MAX_ARCHIVE);
        if (archive == null) throw new IOException("Cached bundle archive is missing");
        verifyArchive(manifest, archive);
        // Never trust previously extracted files. A private mount leaves the durable installation intact.
        Path root = Files.createTempDirectory(this.config.cache(), "stage-");
        try {
            extract(archive, root);
            return root;
        } catch (IOException | RuntimeException exception) {
            delete(root);
            throw exception;
        }
    }

    Path install(BundleManifest manifest, byte[] archive) throws IOException {
        BundleManifest.Release release = manifest.release();
        verifyArchive(manifest, archive);
        Path stage = Files.createTempDirectory(this.config.cache(), "stage-");
        String token = token(release);
        Path target = this.config.cache().resolve("releases").resolve(token);
        boolean keepStage = false;
        try {
            Path root = Files.createDirectory(stage.resolve("web"));
            extract(archive, root);
            Files.write(stage.resolve("bundle.zip"), archive);
            Files.write(stage.resolve("manifest.json"), manifest.envelope());
            if (Files.exists(target)) {
                // Same content identity: repair its durable archive atomically, without replacing mounted files.
                this.write("releases/" + token + "/bundle.zip", archive);
                this.write("releases/" + token + "/manifest.json", manifest.envelope());
                keepStage = true;
                return root;
            }
            Files.move(stage, target, StandardCopyOption.ATOMIC_MOVE);
            return target.resolve("web");
        } finally {
            if (!keepStage) delete(stage);
        }
    }

    private static void verifyArchive(BundleManifest manifest, byte[] archive) throws IOException {
        if (archive.length != manifest.release().size() || !digest(archive).equals(manifest.release().sha256())) {
            throw new IOException("Bundle archive size or SHA-256 does not match the signed manifest");
        }
    }

    BundleManifest manifest(String token) throws IOException {
        checkToken(token);
        byte[] bytes = this.read("releases/" + token + "/manifest.json", BundleManifest.MAX_MANIFEST);
        if (bytes == null) throw new IOException("Cached bundle manifest is missing");
        return BundleManifest.verify(bytes, this.config);
    }

    @Override
    public void close() throws IOException {
        if (this.lock != null) this.lock.close();
        if (this.lockChannel != null) this.lockChannel.close();
    }

    private static void extract(byte[] archive, Path root) throws IOException {
        long total = 0;
        int count = 0;
        Set<String> entries = new HashSet<>();
        Map<String, String> spelling = new HashMap<>();
        byte[] buffer = new byte[16 * 1024];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 4096) throw new IOException("Bundle contains more than 4096 entries");
                String name = entry.getName();
                if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
                if (name.length() > 512 || !entries.add(name.toLowerCase(Locale.ROOT))) throw new IOException("Duplicate or oversized bundle path");
                String[] segments = name.split("/", -1);
                if (segments.length > 32) throw new IOException("Bundle path is too deep");
                Path file = root;
                String prefix = "";
                for (String segment : segments) {
                    if (segment.isEmpty() || segment.equals(".") || segment.equals("..") || segment.endsWith(".")
                            || segment.endsWith(" ") || segment.chars().anyMatch(character -> character < 32 || "\\:<>\"|?*".indexOf(character) >= 0)
                            || segment.matches("(?i)(CON|PRN|AUX|NUL|COM[0-9]|LPT[0-9])(?:\\..*)?")) {
                        throw new IOException("Unsafe bundle path: " + name);
                    }
                    prefix += "/" + segment;
                    String previous = spelling.putIfAbsent(prefix.toLowerCase(Locale.ROOT), prefix);
                    if (previous != null && !previous.equals(prefix)) throw new IOException("Case-colliding bundle paths");
                    file = file.resolve(segment);
                }
                if (entry.isDirectory()) {
                    if (zip.read() != -1) throw new IOException("Bundle directory entry contains file data");
                    Files.createDirectories(file);
                } else {
                    Files.createDirectories(file.getParent());
                    long size = 0;
                    try (OutputStream output = Files.newOutputStream(file, StandardOpenOption.CREATE_NEW)) {
                        int read;
                        while ((read = zip.read(buffer)) != -1) {
                            if (Thread.currentThread().isInterrupted()) throw new IOException("Bundle installation interrupted");
                            size += read;
                            total += read;
                            if (size > 32 * 1024 * 1024 || total > 256 * 1024 * 1024) throw new IOException("Bundle decompression limit exceeded");
                            output.write(buffer, 0, read);
                        }
                    }
                }
                zip.closeEntry();
            }
        }
        if (!Files.isRegularFile(root.resolve("index.html"))) throw new IOException("Bundle must contain index.html at its root");
    }

    static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java must provide SHA-256", exception);
        }
    }

    static String token(BundleManifest.Release release) {
        return digest((release.version() + "\n" + release.sha256()).getBytes(StandardCharsets.UTF_8));
    }

    private static void checkToken(String token) throws IOException {
        if (token == null || !token.matches("[a-f0-9]{64}")) throw new IOException("Invalid cached release ID");
    }

    private static void delete(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }
}
