package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.function.IntSupplier;

/**
 * What a running {@link GameTest} step or {@link GameTool} sees: the live client, guarded
 * accessors for the things a test always needs (player, level, the in-process integrated server),
 * the runner's screenshot sink and its rendered-frame count. Accessors throw with a clear message
 * instead of returning null, so a step that runs too early fails loudly in the report rather than
 * NPE-ing three lines later.
 */
@Environment(EnvType.CLIENT)
public final class TestContext {

    static final Logger LOGGER = LoggerFactory.getLogger("glue-gametest");

    /** Where {@link #saveScreenshot} lands: the runner owns naming, numbering and the output dir. */
    @FunctionalInterface
    public interface ScreenshotSink {
        void save(String label, Consumer<ScreenshotOutcome> onDone);
    }

    /**
     * How one capture ended. Minecraft writes the PNG on an I/O thread and reports both outcomes the
     * same way, so a step that waits for a screenshot has to be told which one it got &mdash; a run
     * that passes with no image on disk is worse than one that fails.
     *
     * @param saved  whether the PNG reached the disk
     * @param detail the file it wrote when it did, the reason it did not otherwise
     */
    public record ScreenshotOutcome(boolean saved, String detail) {
    }

    private final Minecraft client;
    private final ScreenshotSink screenshots;
    private final IntSupplier renderedWorldFrames;

    TestContext(Minecraft client, ScreenshotSink screenshots, IntSupplier renderedWorldFrames) {
        this.client = client;
        this.screenshots = screenshots;
        this.renderedWorldFrames = renderedWorldFrames;
    }

    public Minecraft client() {
        return client;
    }

    public LocalPlayer player() {
        LocalPlayer player = client.player;
        if (player == null) {
            throw new IllegalStateException("no player (world not loaded yet?)");
        }
        return player;
    }

    public ClientLevel level() {
        ClientLevel level = client.level;
        if (level == null) {
            throw new IllegalStateException("no level (world not loaded yet?)");
        }
        return level;
    }

    public MinecraftServer server() {
        MinecraftServer server = client.getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("no integrated server (not singleplayer?)");
        }
        return server;
    }

    /**
     * World frames drawn since the run was armed. Ticks and frames are different clocks &mdash;
     * after a long synchronous reload the client catches up several ticks without drawing anything
     * &mdash; so a step that must observe real rendering counts these instead of ticks. Only frames
     * that render a level advance the count: it stands still on the title screen.
     */
    public int renderedWorldFrames() {
        return renderedWorldFrames.getAsInt();
    }

    /**
     * Saves a screenshot of the last rendered frame. {@code onDone} fires once the capture has ended,
     * on Minecraft's screenshot I/O thread rather than the client thread, so a step that waits on it
     * has to publish the outcome across those two threads safely.
     */
    public void saveScreenshot(String label, Consumer<ScreenshotOutcome> onDone) {
        screenshots.save(label, onDone);
    }

    /** Writes a diagnostic line to the game log. */
    public void log(String message) {
        LOGGER.info("{}", message);
    }
}
