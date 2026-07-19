package fr.lacaleche.glue.client.file;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of communicating to the OS to open a file dialog.
 */
public interface FileDialogInterface {
    void init() throws Exception;

    CompletableFuture<Optional<String>> showSaveDialog(@Nullable String defaultPath,
                                                       @Nullable String defaultName, FileDialogs.FileFilter... filters);

    CompletableFuture<Optional<String>> showOpenDialog(@Nullable String defaultPath, FileDialogs.FileFilter... filters);

    CompletableFuture<Optional<String>> showOpenFolderDialog(@Nullable String defaultPath);
}
