# File Dialogs

`fr.lacaleche.glue.client.file.FileDialogs` — open native OS file dialogs (open, save, pick folder) from your mod.

Uses [LWJGL NativeFileDialog (NFD)](https://github.com/LWJGL/lwjgl3/tree/master/modules/lwjgl/nfd) under the hood, which delegates to the platform's native file picker (Windows Explorer, macOS Finder, GTK/KDE on Linux).

## Quick Start

All methods return a `CompletableFuture<Optional<String>>` that completes when the user closes the dialog:
- `Optional.of(path)` — the user picked a file/folder
- `Optional.empty()` — the user cancelled

```java
import fr.lacaleche.glue.client.file.FileDialogs;
import fr.lacaleche.glue.client.file.FileDialogs.FileFilter;

// Open file
FileDialogs.showOpenDialog(null).thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Opened: " + path));
});

// Save file
FileDialogs.showSaveDialog(null, "my_file.json",
    new FileFilter("JSON Files", "json"),
    new FileFilter("All Files", "*")
).thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Save to: " + path));
});

// Pick folder
FileDialogs.showOpenFolderDialog(null).thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Folder: " + path));
});
```

## API Reference

### `FileDialogs.showOpenDialog`

```java
public static CompletableFuture<Optional<String>> showOpenDialog(
    @Nullable String defaultPath,
    FileFilter... filters
)
```

Opens a native "Open File" dialog.

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory to open. `null` = OS default. |
| `filters`     | File type filters. Empty = accept all files. |

### `FileDialogs.showSaveDialog`

```java
public static CompletableFuture<Optional<String>> showSaveDialog(
    @Nullable String defaultPath,
    @Nullable String defaultName,
    FileFilter... filters
)
```

Opens a native "Save File" dialog.

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory. `null` = OS default. |
| `defaultName` | Pre-filled filename. `null` = empty. |
| `filters`     | File type filters. Empty = accept all files. |

### `FileDialogs.showOpenFolderDialog`

```java
public static CompletableFuture<Optional<String>> showOpenFolderDialog(
    @Nullable String defaultPath
)
```

Opens a native folder picker dialog.

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory. `null` = OS default. |

### `FileDialogs.FileFilter`

```java
public record FileFilter(String name, String... extensions)
```

Defines a file type filter shown in the dialog dropdown.

| Field        | Description | Example |
|--------------|-------------|---------|
| `name`       | Display name for the filter | `"Image Files"` |
| `extensions` | Accepted file extensions (without dots) | `"png", "jpg", "jpeg"` |

Extensions are normalized: leading `*.`, `*`, or `.` prefixes are stripped automatically.

## Threading

The dialog runs on a **background thread** (except on macOS, where it must run on the main thread due to OS restrictions). The returned `CompletableFuture` completes on that background thread.

**Important:** If you need to update UI or game state from the result, route it back to the render thread:

```java
FileDialogs.showOpenDialog(null).thenAccept(result -> {
    Minecraft.getInstance().execute(() -> {
        // Safe to update game state here
        result.ifPresent(path -> loadFile(path));
    });
});
```

## Testmod

Press **F10** in the testmod to open the `FileDialogTestScreen`, which provides buttons for all three dialog types plus a filtered open dialog (images + JSON).
