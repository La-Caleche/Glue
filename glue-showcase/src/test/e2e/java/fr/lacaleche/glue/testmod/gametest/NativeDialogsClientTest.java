package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.client.file.FileDialogs;
import fr.lacaleche.glue.gametest.ClientTest;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Explicitly selected, human-assisted scenario: cancel each real OS dialog when it appears. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@ClientTestSpec(explicitOnly = true)
public final class NativeDialogsClientTest implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        this.expectCancellation(context, "open-file dialog", () -> FileDialogs.showOpenDialog(null,
                new FileDialogs.FileFilter("Images", "*.png", "jpg,jpeg")));
        this.expectCancellation(context, "save-file dialog", () -> FileDialogs.showSaveDialogInDefaultFolder("cancel-this-dialog.txt",
                new FileDialogs.FileFilter("Text Files", ".txt", "md,")));
        this.expectCancellation(context, "folder dialog", FileDialogs::showOpenFolderDialog);
    }

    private void expectCancellation(ClientGameTestContext context, String description, Supplier<CompletableFuture<Optional<String>>> open) {
        Optional<String> result = new ClientTest(context).await("cancel " + description,
                context.computeOnClient(client -> open.get()), Duration.ofMinutes(2));
        if (result.isPresent()) throw new AssertionError("Expected cancellation of " + description + ", selected " + result.orElseThrow());
    }
}
