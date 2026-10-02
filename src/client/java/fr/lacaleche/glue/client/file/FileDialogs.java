package fr.lacaleche.glue.client.file;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Allows code to open system file dialogs for loading &amp; saving.
 * <p>
 * Uses LWJGL's NativeFileDialog (NFD) for OS-native file pickers.
 * NFD is initialized lazily on first use and released when the client stops.
 * <p>
 * Every entry point reports through the returned future and never throws: an empty
 * {@link Optional} means the user cancelled, while any failure — including a failure to
 * initialize NFD — completes the future exceptionally. A caller that guards on "a dialog is
 * already open" therefore always receives exactly one completion, and a genuine error stays
 * distinguishable from a cancellation.
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

    private static synchronized FileDialogInterface init() {
        if (impl == null) {
            try {
                NFDFileDialog nfd = new NFDFileDialog();
                nfd.init();
                impl = nfd;
                // Registered here rather than at client init: the dialog backend is created on first
                // use, and a session that never opens a dialog has nothing to release.
                ClientLifecycleEvents.CLIENT_STOPPING.register(client -> nfd.shutdown());
            } catch (Throwable e) {
                // NFD is a native library: a missing or incompatible binary surfaces as a LinkageError,
                // not an Exception. Wrapping it keeps every initialization failure reportable through the future.
                throw new IllegalStateException("Failed to initialize the native file dialog system", e);
            }
        }
        return impl;
    }

    /**
     * Show the save file dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @param defaultName Default filename to save with. If {@code null}, left to the discretion of the OS.
     * @param filters     A list of file filters to use. If empty, all files are accepted.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showSaveDialog(@Nullable String defaultPath,
                                                                     @Nullable String defaultName, FileFilter... filters) {
        return show(dialogs -> dialogs.showSaveDialog(defaultPath, defaultName, filters));
    }

    /**
     * Show the save file dialog, letting the OS choose the starting folder.
     *
     * <p>This is a separate name rather than a {@code showSaveDialog(String, FileFilter...)}
     * overload, which would overlap {@link #showSaveDialog(String, String, FileFilter...)} through
     * varargs: {@code showSaveDialog(folder, null)} would silently bind {@code folder} to the
     * filename parameter, and {@code showSaveDialog(folder, null, filter)} would not compile at
     * all. {@link #showOpenDialog()} can stay an overload because it takes no arguments and so
     * overlaps nothing; a save dialog still needs its filename, which is why it needs its own
     * name instead.</p>
     *
     * @param defaultName Default filename to save with. If {@code null}, left to the discretion of the OS.
     * @param filters     A list of file filters to use. If empty, all files are accepted.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showSaveDialogInDefaultFolder(@Nullable String defaultName,
                                                                                    FileFilter... filters) {
        return showSaveDialog(null, defaultName, filters);
    }

    /**
     * Show the open file dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @param filters     A list of file filters to use. If empty, all files are accepted.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showOpenDialog(@Nullable String defaultPath, FileFilter... filters) {
        return show(dialogs -> dialogs.showOpenDialog(defaultPath, filters));
    }

    /**
     * Show the open file dialog accepting all files, letting the OS choose the starting folder.
     *
     * <p>To filter file types, use {@link #showOpenDialog(String, FileFilter...)} with a
     * {@code null} path: a {@code showOpenDialog(FileFilter...)} overload would make existing
     * {@code showOpenDialog(null, filters...)} calls ambiguous, because a {@code null} first
     * argument matches both {@code String} and {@code FileFilter}.</p>
     *
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showOpenDialog() {
        return showOpenDialog(null);
    }

    /**
     * Show the open folder dialog.
     *
     * @param defaultPath Default folder to open to. If {@code null}, left to the discretion of the OS.
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showOpenFolderDialog(@Nullable String defaultPath) {
        return show(dialogs -> dialogs.showOpenFolderDialog(defaultPath));
    }

    /**
     * Show the open folder dialog, letting the OS choose the starting folder.
     *
     * @return A future that completes once the dialog has closed, containing the chosen path or empty if cancelled,
     * or completing exceptionally if the dialog could not be shown.
     */
    public static CompletableFuture<Optional<String>> showOpenFolderDialog() {
        return showOpenFolderDialog(null);
    }

    private static CompletableFuture<Optional<String>> show(
            Function<FileDialogInterface, CompletableFuture<Optional<String>>> dialog) {
        try {
            return dialog.apply(init()).whenComplete((path, error) -> logFailure(error));
        } catch (RuntimeException e) {
            // Lazy initialization and executor submission happen before any future exists; without this
            // the caller would get a synchronous throw instead of the completion it is waiting on.
            logFailure(e);
            return CompletableFuture.failedFuture(e);
        }
    }

    private static void logFailure(@Nullable Throwable error) {
        if (error == null) return;

        LOGGER.error("Error opening file dialog", error);
    }
}
