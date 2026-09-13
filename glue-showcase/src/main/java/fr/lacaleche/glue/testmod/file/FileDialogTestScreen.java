package fr.lacaleche.glue.testmod.file;

import fr.lacaleche.glue.client.file.FileDialogs;
import fr.lacaleche.glue.mcsx.client.Ui;
import fr.lacaleche.glue.mcsx.client.reactive.Signal;
import fr.lacaleche.glue.mcsx.client.style.Stylesheets;
import fr.lacaleche.glue.mcsx.client.theme.Themes;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.mcsx.ShowcaseUiScreen;
import icyllis.modernui.core.Core;
import icyllis.modernui.view.View;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** MCSX screen demonstrating open, save, filtered-file, and folder dialogs. */
public final class FileDialogTestScreen extends ShowcaseUiScreen {

    public static final String TAG_ROOT = "showcase.file-dialogs";
    public static final String TAG_STATUS = "showcase.file-dialogs.status";

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDialogTestScreen.class);

    private final Signal<String> lastResult = Signal.of(translated("showcase.files.status.ready"));
    private final Signal<Boolean> waiting = Signal.of(false);

    @Override
    protected View create(Ui ui) {
        return ui.screen(
                Signal.of(Themes.mcsx()),
                Stylesheets.resource(TestmodClient.id("showcase-controls")),
                ui.card(
                        ui.column(
                                ui.heading("showcase.files.title"),
                                ui.copy("showcase.files.description")
                        ).classes("control-header"),
                        ui.column(
                                ui.button("showcase.files.open", this::openFile).enabled(this.waiting.map(value -> !value)),
                                ui.secondaryButton("showcase.files.save", this::saveFile)
                                        .enabled(this.waiting.map(value -> !value)),
                                ui.secondaryButton("showcase.files.folder", this::openFolder)
                                        .enabled(this.waiting.map(value -> !value)),
                                ui.secondaryButton("showcase.files.filtered", this::openFiltered)
                                        .enabled(this.waiting.map(value -> !value))
                        ).classes("dialog-actions"),
                        ui.row(
                                ui.text(this.lastResult).tag(TAG_STATUS).classes("dialog-status"),
                                ui.quietButton("showcase.back", this::back)
                        ).classes("control-footer")
                ).classes("dialog-card")
        ).tag(TAG_ROOT).classes("showcase-dialogs");
    }

    /**
     * Refuses duplicate requests and publishes every asynchronous outcome through MCSX's UI-thread
     * bridge. Cancellation remains distinct from exceptional completion.
     */
    private void show(String labelKey, Supplier<CompletableFuture<Optional<String>>> dialog) {
        if (this.waiting.get()) return;

        String label = translated(labelKey);
        CompletableFuture<Optional<String>> pending;
        try {
            pending = dialog.get();
        } catch (RuntimeException failure) {
            this.lastResult.set(translated("showcase.files.status.failed", label, failure.getMessage()));
            LOGGER.error("{} could not be opened", label, failure);
            return;
        }

        this.waiting.set(true);
        this.lastResult.set(translated("showcase.files.status.waiting"));
        pending.whenComplete((result, error) -> Core.postOnUiThread(() -> {
            this.waiting.set(false);
            if (error != null) {
                this.lastResult.set(translated("showcase.files.status.failed", label, error.getMessage()));
                LOGGER.error("{} failed", label, error);
                return;
            }

            String message = result
                    .map(path -> translated("showcase.files.status.selected", label, path))
                    .orElseGet(() -> translated("showcase.files.status.cancelled"));
            this.lastResult.set(message);
            LOGGER.info("{} result: {}", label, message);
        }));
    }

    private void openFile() {
        this.show("showcase.files.result.opened", FileDialogs::showOpenDialog);
    }

    private void saveFile() {
        this.show("showcase.files.result.save", () -> FileDialogs.showSaveDialogInDefaultFolder(
                "untitled.txt",
                new FileDialogs.FileFilter("Text Files", "txt", "md")
        ));
    }

    private void openFolder() {
        this.show("showcase.files.result.folder", FileDialogs::showOpenFolderDialog);
    }

    private void openFiltered() {
        this.show("showcase.files.result.opened", () -> FileDialogs.showOpenDialog(
                null,
                new FileDialogs.FileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"),
                new FileDialogs.FileFilter("JSON Files", "json")
        ));
    }

    private static String translated(String key, Object... arguments) {
        return Component.translatable(key, arguments).getString();
    }
}
