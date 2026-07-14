package fr.lacaleche.glue.client.file;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Allows code to open system file dialogs for loading &amp; saving.
 * <p>
 * Uses LWJGL's NativeFileDialog (NFD) for OS-native file pickers.
 * NFD is initialized lazily on first use.
 */
public class FileDialogs {
    /**
     * A type of file that may be accepted by a file dialog.
     *
     * @param name       The name of the file type. ex: "JPEG File".
     * @param extensions The file extensions this filter supports. ex: ["jpg", "jpeg"]
     */
    public record FileFilter(String name, String... extensions) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDialogs.class);

    private static FileDialogInterface impl;

    private static synchronized void init() {
        if (impl == null) {
            try {
                NFDFileDialog nfd = new NFDFileDialog();
                nfd.init();
                impl = nfd;
            } catch (Throwable e) {
                LOGGER.error("Error initializing NativeFileDialog.", e);
                throw new RuntimeException("Failed to initialize file dialog system", e);
            }
        }
    }

    /**
     * Show the save file dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @param defaultName Default filename to save with. If {@code null}, left to the discretion of the OS.
     * @param filters     A list of file filters to use. If empty, all files are accepted.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled.
     */
    public static CompletableFuture<Optional<String>> showSaveDialog(@Nullable String defaultPath,
                                                                     @Nullable String defaultName, FileFilter... filters) {
        init();
        return impl.showSaveDialog(defaultPath, defaultName, filters).exceptionally(FileDialogs::handle);
    }

    /**
     * Show the open file dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @param filters     A list of file filters to use. If empty, all files are accepted.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled.
     */
    public static CompletableFuture<Optional<String>> showOpenDialog(@Nullable String defaultPath, FileFilter... filters) {
        init();
        return impl.showOpenDialog(defaultPath, filters).exceptionally(FileDialogs::handle);
    }

    /**
     * Show the open folder dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled.
     */
    public static CompletableFuture<Optional<String>> showOpenFolderDialog(@Nullable String defaultPath) {
        init();
        return impl.showOpenFolderDialog(defaultPath).exceptionally(FileDialogs::handle);
    }

    private static Optional<String> handle(Throwable e) {
        LOGGER.error("Error opening file dialog", e);
        return Optional.empty();
    }
}
