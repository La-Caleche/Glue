package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.testmod.TestmodClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Drives one {@link GameTest} through a live client session. Armed by
 * {@code -Dglue.showcase.gametest=<name>} (from gradle: {@code -Pglue.showcase.gametest=<name>},
 * typically with {@code -Pglue.showcase.quickplay=<world>} so the run needs no keyboard): executes
 * the test's steps one at a time on the client tick, saves its screenshots and a per-step
 * {@code report.txt} under {@code screenshots/gametest/<name>/}, then closes the game &mdash;
 * unless {@code -Dglue.showcase.gametest.keepOpen=true}, which leaves the session up for a human.
 *
 * <p>A step that throws or exceeds its timeout fails the test: a final {@code FAILED} screenshot
 * is taken, the remaining steps are skipped, and the report ends with {@code RESULT: FAIL}.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameTestRunner {

    private static final Map<String, GameTest> TESTS = new LinkedHashMap<>();
    /** Ticks between finishing (report written) and closing the game: lets async PNG writes land. */
    private static final int QUIT_DELAY = 60;

    public static void register(GameTest test) {
        TESTS.put(test.name(), test);
    }

    public static void init() {
        GameTests.registerAll();
        String name = System.getProperty("glue.showcase.gametest");
        if (name == null || name.isEmpty()) return;
        GameTest test = TESTS.get(name);
        if (test == null) {
            TestmodClient.LOGGER.error("[gametest] unknown test '{}' -- available: {}",
                    name, TESTS.keySet());
            return;
        }
        GameTestRunner runner = new GameTestRunner(test);
        ClientTickEvents.END_CLIENT_TICK.register(runner::tick);
        TestmodClient.LOGGER.info("[gametest] armed: {} ({} steps)", name, test.steps().size());
    }

    private final GameTest test;
    private final List<String> report = new ArrayList<>();
    private TestContext context;
    private File outputDir;
    private int stepIndex;
    private int ticksInStep;
    private int shotCount;
    private boolean finished;
    private int quitDelay = -1;

    private GameTestRunner(GameTest test) {
        this.test = test;
    }

    private void tick(Minecraft client) {
        if (finished) {
            if (quitDelay > 0 && --quitDelay == 0) client.stop();
            return;
        }
        if (context == null) {
            outputDir = new File(new File(client.gameDirectory, "screenshots"),
                    "gametest/" + test.name());
            outputDir.mkdirs();
            context = new TestContext(client, this::saveScreenshot);
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

    private void record(String status, GameTest.Step step, String note) {
        String line = String.format("[%2d/%2d] %s  %s  (%d ticks)%s",
                stepIndex + 1, test.steps().size(), status, step.description(), ticksInStep,
                note == null ? "" : "  -- " + note);
        report.add(line);
        TestmodClient.LOGGER.info("[gametest] {}", line);
    }

    private void failure() {
        saveScreenshot("FAILED", () -> {});
        finish(false);
    }

    private void finish(boolean pass) {
        finished = true;
        report.add("RESULT: " + (pass ? "PASS" : "FAIL"));
        TestmodClient.LOGGER.info("[gametest] {}: {}", test.name(), pass ? "PASS" : "FAIL");
        try {
            Files.write(new File(outputDir, "report.txt").toPath(), report);
        } catch (IOException e) {
            TestmodClient.LOGGER.error("[gametest] could not write report", e);
        }
        if (!Boolean.getBoolean("glue.showcase.gametest.keepOpen")) {
            quitDelay = QUIT_DELAY;
        }
    }

    private void saveScreenshot(String label, Runnable onSaved) {
        Minecraft client = context.client();
        String name = String.format("%02d-%s.png", ++shotCount, label);
        Screenshot.grab(client.gameDirectory, "gametest/" + test.name() + "/" + name,
                client.getMainRenderTarget(), 1, component -> onSaved.run());
    }
}
