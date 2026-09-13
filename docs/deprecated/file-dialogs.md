# File Dialogs

`fr.lacaleche.glue.client.file.FileDialogs` — open native OS file dialogs (open, save, pick folder) from your mod.

Uses [LWJGL NativeFileDialog (NFD)](https://github.com/LWJGL/lwjgl3/tree/master/modules/lwjgl/nfd) under the hood, which delegates to the platform's native file picker (Windows Explorer, macOS Finder, GTK/KDE on Linux).

## Quick Start

All methods return a `CompletableFuture<Optional<String>>` that completes when the user closes the dialog:
- `Optional.of(path)` — the user picked a file/folder
- `Optional.empty()` — the user cancelled
- *completed exceptionally* — the dialog could not be shown (see [Failures](#failures))

```java
import fr.lacaleche.glue.client.file.FileDialogs;
import fr.lacaleche.glue.client.file.FileDialogs.FileFilter;

// Open file
FileDialogs.showOpenDialog().thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Opened: " + path));
});

// Save file
FileDialogs.showSaveDialogInDefaultFolder("my_file.json",
    new FileFilter("JSON Files", "json")
).thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Save to: " + path));
});

// Pick folder
FileDialogs.showOpenFolderDialog().thenAccept(result -> {
    result.ifPresent(path -> System.out.println("Folder: " + path));
});
```

Each dialog also has a form with a leading `defaultPath` argument to open in a specific starting
folder; the short forms above leave that choice to the OS.

Neither short form is an overload that overlaps the `defaultPath` form, because varargs would make
such an overload silently pick the wrong method:

- An open dialog *with filters* uses the `defaultPath` form with a `null` path
  (`showOpenDialog(null, filters...)`) — a `showOpenDialog(FileFilter...)` overload would make that
  long-standing call shape ambiguous.
- A save dialog always needs its filename, so its short form gets its own name,
  `showSaveDialogInDefaultFolder(defaultName, filters...)`. As an overload,
  `showSaveDialog(folder, null)` would have bound `folder` to the *filename* parameter and
  `showSaveDialog(folder, null, filter)` would not have compiled at all.

## API Reference

### `FileDialogs.showOpenDialog`

```java
public static CompletableFuture<Optional<String>> showOpenDialog()

public static CompletableFuture<Optional<String>> showOpenDialog(
    @Nullable String defaultPath,
    FileFilter... filters
)
```

Opens a native "Open File" dialog. The zero-argument form accepts all files; to filter file
types, use the `defaultPath` form (pass `null` for the OS-default folder).

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory to open. Omitted or `null` = OS default. |
| `filters`     | File type filters. Empty = accept all files. |

### `FileDialogs.showSaveDialog`

```java
public static CompletableFuture<Optional<String>> showSaveDialogInDefaultFolder(
    @Nullable String defaultName,
    FileFilter... filters
)

public static CompletableFuture<Optional<String>> showSaveDialog(
    @Nullable String defaultPath,
    @Nullable String defaultName,
    FileFilter... filters
)
```

Opens a native "Save File" dialog. `showSaveDialogInDefaultFolder` is the same call with the
folder left to the OS; it is a distinct name rather than an overload for the reason above.

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory. Omitted or `null` = OS default. |
| `defaultName` | Pre-filled filename. `null` = empty. |
| `filters`     | File type filters. Empty = accept all files. |

### `FileDialogs.showOpenFolderDialog`

```java
public static CompletableFuture<Optional<String>> showOpenFolderDialog()

public static CompletableFuture<Optional<String>> showOpenFolderDialog(
    @Nullable String defaultPath
)
```

Opens a native folder picker dialog.

| Parameter     | Description |
|---------------|-------------|
| `defaultPath` | Initial directory. Omitted or `null` = OS default. |

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

Do not add an "All Files" filter of your own. NFD adds a wildcard entry to every dialog on every
platform, and it requires each specification it is handed to be non-empty — an empty one is
documented as undefined behaviour. Every conventional spelling of it — `"*"`, `"*.*"`, `".*"`,
`"**"` — normalizes to nothing, and a filter left with no usable extension is dropped rather than
passed on. Commas are honoured too: `"png,jpg"` in one string becomes two extensions, and the empty
token in `"png,"` is discarded instead of reaching NFD.

## Failures

A dialog that cannot be shown — NFD failed to initialize, the native picker returned an error —
completes the future **exceptionally**; it never resolves to `Optional.empty()`. An empty result
therefore always means "the user cancelled", and a real failure stays distinguishable from one.

Nothing is thrown synchronously either: NFD is initialized lazily inside the call, and an
initialization failure becomes a failed future rather than an exception thrown at the call site.
A caller that raises a "a dialog is already open" guard before calling always gets exactly one
completion and can always lower it again.

Observe both outcomes with `whenComplete` (or `handle` / `exceptionally`), not `thenAccept`:

```java
private boolean waiting;

void openFile() {
    if (waiting) return;
    waiting = true;

    FileDialogs.showOpenDialog().whenComplete((result, error) -> Minecraft.getInstance().execute(() -> {
        waiting = false;
        if (error != null) {
            // The dialog failed - the user did not cancel.
            return;
        }
        result.ifPresent(this::loadFile);
    }));
}
```

Glue logs the failure before propagating it, so a caller that only uses `thenAccept` still leaves
a trace in the log.

## Threading

The dialog runs on a **background thread** (except on macOS, where it must run on the main thread due to OS restrictions). The returned `CompletableFuture` completes on that background thread.

**Important:** If you need to update UI or game state from the result, route it back to the render thread:

```java
FileDialogs.showOpenDialog().thenAccept(result -> {
    Minecraft.getInstance().execute(() -> {
        // Safe to update game state here
        result.ifPresent(path -> loadFile(path));
    });
});
```

## Testmod

Press **F10** in the testmod to open the `FileDialogTestScreen`, which provides buttons for all three dialog types plus a filtered open dialog (images + JSON).
