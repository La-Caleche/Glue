package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives one {@link GameTest} through a live client session. Armed by
 * {@code -Dglue.gametest=<name>}: executes the test's steps one at a time on the client tick,
 * saves its screenshots and a per-step {@code report.txt} under
 * {@code screenshots/gametest/<name>/}, then closes the game &mdash; unless
 * {@code -Dglue.gametest.keepOpen=true}, which leaves the session up for a human.
 *
 * <p>The test is resolved on the first tick, after every mod's client entrypoint has run, so a
 * registering mod needs no dependency ordering against this module. A step that throws or exceeds
 * its timeout fails the test: a final {@code FAILED} screenshot is taken, the remaining steps are
 * skipped, and the report ends with {@code RESULT: FAIL}.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameTestRunner {

    /** Ticks between finishing (report written) and closing the game: lets async PNG writes land. */
    private static final int QUIT_DELAY = 60;

    private final String testName;
    /** The name as a filesystem-safe folder: {@code ignis:editor-smoke} → {@code ignis_editor-smoke}. */
    private final String folderName;
    private final List<String> report = new ArrayList<>();
    private GameTest test;
    private TestContext context;
    private File outputDir;
    private int stepIndex;
    private int ticksInStep;
    private int shotCount;
    private boolean finished;
    private int quitDelay = -1;

    GameTestRunner(String testName) {
        this.testName = testName;
        this.folderName = testName.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    void tick(Minecraft client) {
        if (finished) {
            if (quitDelay > 0 && --quitDelay == 0) {
                client.stop();
            }
            return;
        }
        if (test == null && !resolve(client)) {
            return;
        }
        if (stepIndex >= test.steps().size()) {
            finish(true);
            return;
        }

        GameTest.Step step = test.steps().get(stepIndex);
        ticksInStep++;
        try {
            if (step.tick().tick(context)) {
                record("PASS", step, null);
                stepIndex++;
                ticksInStep = 0;
            } else if (ticksInStep > step.timeoutTicks()) {
                record("FAIL", step, "timed out after " + ticksInStep + " ticks");
                failure();
            }
        } catch (Throwable t) {
            record("FAIL", step, t.toString());
            failure();
        }
    }

    private boolean resolve(Minecraft client) {
        test = GameTests.get(testName);
        if (test == null) {
            TestContext.LOGGER.error("[gametest] unknown test '{}' -- available: {}",
                    testName, GameTests.names());
            finished = true;
            return false;
        }
        outputDir = new File(new File(client.gameDirectory, "screenshots"), "gametest/" + folderName);
        outputDir.mkdirs();
        context = new TestContext(client, this::saveScreenshot);
        TestContext.LOGGER.info("[gametest] running: {} ({} steps)", testName, test.steps().size());
        return true;
    }

    private void record(String status, GameTest.Step step, String note) {
        String line = String.format("[%2d/%2d] %s  %s  (%d ticks)%s",
                stepIndex + 1, test.steps().size(), status, step.description(), ticksInStep,
                note == null ? "" : "  -- " + note);
        report.add(line);
        TestContext.LOGGER.info("[gametest] {}", line);
    }

    private void failure() {
        saveScreenshot("FAILED", () -> { });
        finish(false);
    }

    private void finish(boolean pass) {
        finished = true;
        report.add("RESULT: " + (pass ? "PASS" : "FAIL"));
        TestContext.LOGGER.info("[gametest] {}: {}", testName, pass ? "PASS" : "FAIL");
        try {
            Files.write(new File(outputDir, "report.txt").toPath(), report);
        } catch (IOException e) {
            TestContext.LOGGER.error("[gametest] could not write report", e);
        }
        if (!Boolean.getBoolean("glue.gametest.keepOpen")) {
            quitDelay = QUIT_DELAY;
        }
    }

    private void saveScreenshot(String label, Runnable onSaved) {
        Minecraft client = context.client();
        String name = String.format("%02d-%s.png", ++shotCount, label);
        Screenshot.grab(client.gameDirectory, "gametest/" + folderName + "/" + name,
                client.getMainRenderTarget(), 1, component -> onSaved.run());
    }
}
