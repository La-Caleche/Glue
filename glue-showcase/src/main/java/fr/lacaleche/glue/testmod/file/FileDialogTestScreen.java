package fr.lacaleche.glue.testmod.file;

import fr.lacaleche.glue.client.file.FileDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Test screen demonstrating the FileDialogs API.
 * Provides buttons for open file, save file, and open folder dialogs.
 */
public class FileDialogTestScreen extends Screen {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileDialogTestScreen.class);

    private String lastResult = "No dialog opened yet";
    private boolean waiting = false;

    public FileDialogTestScreen() {
        super(Component.literal("File Dialog Test"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 50;

        addRenderableWidget(Button.builder(Component.literal("Open File..."), btn -> openFile())
                .pos(centerX - 100, y)
                .size(200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Save File..."), btn -> saveFile())
                .pos(centerX - 100, y + 25)
                .size(200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Open Folder..."), btn -> openFolder())
                .pos(centerX - 100, y + 50)
                .size(200, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("Open (with filter)..."), btn -> openFiltered())
                .pos(centerX - 100, y + 75)
                .size(200, 20)
                .build());
    }

    /**
     * The envelope every dialog button shares: refuse while one is open, show progress, then apply the
     * result back on the client thread. Only the dialog call and the label differ.
     */
    private void show(String label, Supplier<CompletableFuture<Optional<String>>> dialog) {
        if (waiting) return;
        waiting = true;
        lastResult = "Waiting for dialog...";

        dialog.get().thenAccept(result -> Minecraft.getInstance().execute(() -> {
            lastResult = result.map(path -> label + ": " + path).orElse("Cancelled");
            waiting = false;
            LOGGER.info("{} result: {}", label, lastResult);
        }));
    }

    private void openFile() {
        show("Opened", () -> FileDialogs.showOpenDialog(null));
    }

    private void saveFile() {
        show("Save to", () -> FileDialogs.showSaveDialog(null, "untitled.txt",
                new FileDialogs.FileFilter("Text Files", "txt", "md"),
                new FileDialogs.FileFilter("All Files", "*")));
    }

    private void openFolder() {
        show("Folder", () -> FileDialogs.showOpenFolderDialog(null));
    }

    private void openFiltered() {
        show("Opened", () -> FileDialogs.showOpenDialog(null,
                new FileDialogs.FileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"),
                new FileDialogs.FileFilter("JSON Files", "json")));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(font, lastResult, this.width / 2, this.height / 2 + 50, 0xAAFFAA);
        guiGraphics.drawCenteredString(font, "ESC to close", this.width / 2, this.height - 20, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
