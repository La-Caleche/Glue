package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What a running {@link GameTest} step or {@link GameTool} sees: the live client, guarded
 * accessors for the things a test always needs (player, level, the in-process integrated server),
 * and the runner's screenshot sink. Accessors throw with a clear message instead of returning
 * null, so a step that runs too early fails loudly in the report rather than NPE-ing three lines
 * later.
 */
@Environment(EnvType.CLIENT)
public final class TestContext {

    static final Logger LOGGER = LoggerFactory.getLogger("glue-gametest");

    /** Where {@link #saveScreenshot} lands: the runner owns naming, numbering and the output dir. */
    @FunctionalInterface
    public interface ScreenshotSink {
        void save(String label, Runnable onSaved);
    }

    private final Minecraft client;
    private final ScreenshotSink screenshots;

    TestContext(Minecraft client, ScreenshotSink screenshots) {
        this.client = client;
        this.screenshots = screenshots;
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

    /** Saves a screenshot of the last rendered frame; {@code onSaved} fires once the PNG is on disk. */
    public void saveScreenshot(String label, Runnable onSaved) {
        screenshots.save(label, onSaved);
    }

    /** Logs into the game log; the line also lands in the step report via the runner's log. */
    public void log(String message) {
        LOGGER.info("{}", message);
    }
}
