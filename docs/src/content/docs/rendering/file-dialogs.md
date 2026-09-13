---
title: File Dialogs
description: Choose native files and hand asynchronous results back to the Minecraft client thread safely.
artifact: glue-render
modId: glue-render
environment: client
---

# File Dialogs

This task opens the operating system's file picker and displays the chosen resource path in a Light
Workshop editor screen. Use `FileDialogs` for native open, save, and folder selection without
blocking the client on Windows or Linux.

## Show the First Result

Start with one open-file action and one screen field:

```java [src/client/java/dev/example/lightworkshop/screen/WorkshopEditorScreen.java]
private String selectedFile = "No file selected";

private void chooseTexture() {
    FileDialogs.showOpenDialog(
            null,
            new FileDialogs.FileFilter("PNG textures", "png"))
        .thenAccept(selection -> Minecraft.getInstance().execute(() ->
                selectedFile = selection.orElse("Cancelled")));
}
```

Call `chooseTexture()` from a screen button. Selecting a PNG changes `selectedFile` to the returned
path; closing the picker without a choice changes it to `Cancelled`. Render that field with the
screen's normal `GuiGraphics` code.

<DocImage title="Native texture picker result" description="A native operating-system file picker filtered to PNG files beside a Light Workshop editor screen whose status reads Selected: pedestal.png." />

Replace this placeholder with a two-panel 1400x700 image from the target operating system. Show the
`PNG textures` filter in the picker and the final selected path in the Minecraft screen; redact the
user-profile portion of the path.

The smallest example intentionally omits duplicate-click and failure UI. Add those before shipping.

## Prevent Duplicates and Handle Every Outcome

`CompletableFuture<Optional<String>>` has three outcomes: a selected path, an empty cancellation, or
exceptional completion. Use `whenComplete` so a failure also releases the open-dialog guard, then
marshal all screen and game state back to Minecraft's client thread:

```java [src/client/java/dev/example/lightworkshop/screen/WorkshopEditorScreen.java]
private boolean dialogOpen;
private String selectedFile = "No file selected";

private void chooseTexture() {
    if (dialogOpen) return;
    dialogOpen = true;

    FileDialogs.showOpenDialog(
            null,
            new FileDialogs.FileFilter("PNG textures", "png"))
        .whenComplete((selection, error) -> Minecraft.getInstance().execute(() -> {
            dialogOpen = false;

            if (error != null) {
                selectedFile = "Picker failed: " + error.getMessage();
                return;
            }

            selectedFile = selection.orElse("Cancelled");
        }));
}
```

Disable the button while `dialogOpen` is true. Do not use `thenAccept` for cleanup: it is skipped on
exceptional completion.

For an MCSX `UiScreen`, the same completion must cross to ModernUI's UI thread instead of mutating a
bound `Signal` from the dialog worker. `Signal.postSet(...)` is sufficient for a value already built
by the completion; use `Core.postOnUiThread(...)` when one completion updates several signals or
resolves translated display text as one UI operation. Continue to use `Minecraft.execute(...)` for
world, renderer, or other client-thread state. The showcase's native-dialog screen demonstrates the
MCSX form with disabled buttons and a reactive result label.

## Choose the Dialog Type

::: details Open, save, and folder methods

Open any file and let the OS choose the initial folder:

```java
FileDialogs.showOpenDialog();
```

Open with an optional initial path and filters:

```java
FileDialogs.showOpenDialog(defaultPath, filters);
```

There is intentionally no `showOpenDialog(FileFilter...)` overload. Pass `null` for a filtered
picker with no preferred folder.

Save with a path and suggested file name, or let the OS choose the folder:

```java
FileDialogs.showSaveDialog(defaultPath, defaultName, filters);
FileDialogs.showSaveDialogInDefaultFolder(defaultName, filters);
```

Choose a folder:

```java
FileDialogs.showOpenFolderDialog();
FileDialogs.showOpenFolderDialog(defaultPath);
```

Every path and default name may be `null` where annotated. Empty filter lists accept all files.
:::

::: details Filter normalization

Define a display name followed by extensions:

```java
new FileDialogs.FileFilter("Images", "png", "jpg", "jpeg")
```

Glue strips repeated leading `*` and `.` characters, splits comma-separated values, trims blanks,
and drops empty entries. `*.png`, `.png`, and `png` all become `png`.

Do not add an `All Files` filter using `*` or `*.*`. NativeFileDialog already supplies a wildcard
entry, and Glue drops wildcard-only filters rather than passing an invalid empty specification.
Null filters and filters with no usable extensions are also omitted.
:::

## Respect Completion Threads

On Windows and Linux, one daemon dialog thread owns NativeFileDialog initialization, every picker,
and shutdown. Normal future completions run there. On macOS, the OS requires picker work on the
Minecraft client thread, so the game pauses while the native dialog is open.

A setup failure can return an already-failed future whose continuation runs immediately on the
attaching thread. Never rely on one completion thread. Route screen, world, rendering, and game state
through `Minecraft.getInstance().execute(...)` on every platform.

## Let Glue Own Native Shutdown

The backend initializes lazily on first use. Glue registers client shutdown only after successful
initialization and releases NativeFileDialog on its owning thread.

- Public methods report setup, native picker, submission, and shutdown failures through the future.
- Only a user declining an active picker returns `Optional.empty()`.
- New requests fail after shutdown starts.
- Pending request futures are cancelled during shutdown. A native picker already on screen cannot be
  interrupted immediately, but it cannot deliver a successful result afterward.

Treat shutdown cancellation as exceptional completion, not user cancellation.

## Next Steps

- Present imported assets in [3D Scene Viewport](./scene-viewport.md).
- Add non-blocking status text through [Rendering Events](./events.md).
- Check the [Rendering overview](./index.md) before introducing a custom GPU resource for previews.
