package fr.lacaleche.glue.testmod.file;

import fr.lacaleche.glue.client.file.FileDialogs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private void openFile() {
        if (waiting) return;
        waiting = true;
        lastResult = "Waiting for dialog...";

        FileDialogs.showOpenDialog(null).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                lastResult = result.map(p -> "Opened: " + p).orElse("Cancelled");
                waiting = false;
                LOGGER.info("Open result: {}", lastResult);
            });
        });
    }

    private void saveFile() {
        if (waiting) return;
        waiting = true;
        lastResult = "Waiting for dialog...";

        FileDialogs.showSaveDialog(null, "untitled.txt",
                new FileDialogs.FileFilter("Text Files", "txt", "md"),
                new FileDialogs.FileFilter("All Files", "*")
        ).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                lastResult = result.map(p -> "Save to: " + p).orElse("Cancelled");
                waiting = false;
                LOGGER.info("Save result: {}", lastResult);
            });
        });
    }

    private void openFolder() {
        if (waiting) return;
        waiting = true;
        lastResult = "Waiting for dialog...";

        FileDialogs.showOpenFolderDialog(null).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                lastResult = result.map(p -> "Folder: " + p).orElse("Cancelled");
                waiting = false;
                LOGGER.info("Folder result: {}", lastResult);
            });
        });
    }

    private void openFiltered() {
        if (waiting) return;
        waiting = true;
        lastResult = "Waiting for dialog...";

        FileDialogs.showOpenDialog(null,
                new FileDialogs.FileFilter("Images", "png", "jpg", "jpeg", "gif", "bmp"),
                new FileDialogs.FileFilter("JSON Files", "json")
        ).thenAccept(result -> {
            Minecraft.getInstance().execute(() -> {
                lastResult = result.map(p -> "Opened: " + p).orElse("Cancelled");
                waiting = false;
                LOGGER.info("Filtered open result: {}", lastResult);
            });
        });
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        var font = Minecraft.getInstance().font;
        guiGraphics.drawCenteredString(font, this.title, this.width / 2, 20, 0xFFFFFF);
        guiGraphics.drawCenteredString(font, lastResult, this.width / 2, this.height / 2 + 50, 0xAAFFAA);
        guiGraphics.drawCenteredString(font, "ESC to close", this.width / 2, this.height - 20, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
