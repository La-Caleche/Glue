package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Drives one {@link GameTest} through a live client session. Armed by
 * {@code -Dglue.gametest=<name>}: executes the test's steps one at a time on the client tick,
 * saves its screenshots and a per-step {@code report.txt} under
 * {@code screenshots/gametest/<name>/}, then closes the game &mdash; unless
 * {@code -Dglue.gametest.keepOpen=true}, which leaves the session up for a human.
 *
 * <p>The test is resolved on the first tick, after every mod's client entrypoint has run, so a
 * registering mod needs no dependency ordering against this module. A step that throws or exceeds
 * its timeout fails the test: the runner requests a best-effort {@code FAILED} screenshot, skips the
 * remaining steps, writes the failing verdict, and waits {@value #QUIT_DELAY} ticks before closing so
 * asynchronous PNG writes normally have time to finish. That last capture is diagnostic, not
 * guaranteed &mdash; unlike an ordinary {@link GameTest#screenshot} step, which completes only once
 * its write has been reported successful.</p>
 *
 * <p>Every way a run can end &mdash; an unusable output directory, a throwing test factory, an
 * unknown test name, a failing or timed-out step, or success &mdash; goes through {@link #finish},
 * so an unattended run always emits its verdict and always closes the client. The verdict lands in
 * {@code report.txt} next to the screenshots; when that directory is the very thing that failed, it
 * falls back to {@code gametest-<name>-report.txt} in the game directory, and to the log when the
 * filesystem refuses both.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameTestRunner {

    /** Ticks between finishing (report written) and closing the game: lets async PNG writes land. */
    private static final int QUIT_DELAY = 60;
    /** Vanilla reports a written and a failed capture through the same consumer; only the key differs. */
    private static final String SCREENSHOT_SUCCESS = "screenshot.success";

    private final String testName;
    /** The name as a filesystem-safe folder: {@code ignis:editor-smoke} → {@code ignis_editor-smoke}. */
    private final String folderName;
    private final List<String> report = new ArrayList<>();
    private GameTest test;
    private TestContext context;
    private File gameDirectory;
    private File outputDir;
    private int stepIndex;
    private int ticksInStep;
    private int shotCount;
    private int renderedFrames;
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

    /** Counts one drawn world frame; the count a step reads through {@link TestContext}. */
    void frameRendered() {
        renderedFrames++;
    }

    /**
     * Prepares the output directory and builds the test on the first tick. Returns false when the
     * run cannot start &mdash; it has then already been finished, so the client still shuts down.
     */
    private boolean resolve(Minecraft client) {
        gameDirectory = client.gameDirectory;
        outputDir = new File(new File(gameDirectory, "screenshots"), "gametest/" + folderName);
        // Nothing else clears the fallback: only the primary directory is swept below.
        clearStaleReport(fallbackReport(gameDirectory, folderName), report);
        if (!prepareOutputDirectory()) {
            abort("could not prepare the output directory " + outputDir);
            return false;
        }

        GameTest resolved;
        try {
            resolved = GameTests.build(testName);
        } catch (Throwable t) {
            // Throwable, not RuntimeException: a mod-supplied factory can fail with a linkage error
            // as easily as with an exception, and either way the run has to end rather than hang.
            TestContext.LOGGER.error("[gametest] could not build test '{}'", testName, t);
            abort("could not build test '" + testName + "': " + t);
            return false;
        }
        if (resolved == null) {
            TestContext.LOGGER.error("[gametest] unknown test '{}' -- available: {}",
                    testName, GameTests.names());
            abort("unknown test '" + testName + "' -- available: " + GameTests.names());
            return false;
        }

        test = resolved;
        context = new TestContext(client, this::saveScreenshot, () -> renderedFrames);
        TestContext.LOGGER.info("[gametest] running: {} ({} steps)", testName, test.steps().size());
        return true;
    }

    private boolean prepareOutputDirectory() {
        try {
            Files.createDirectories(outputDir.toPath());
            File[] artifacts = outputDir.listFiles(File::isFile);
            if (artifacts == null) {
                throw new IOException("could not list " + outputDir);
            }
            for (File artifact : artifacts) {
                Files.delete(artifact.toPath());
            }
            return true;
        } catch (IOException exception) {
            TestContext.LOGGER.error("[gametest] could not prepare the output directory", exception);
            return false;
        }
    }

    /** Where a run's verdict belongs: beside its screenshots, and the path CI reads. */
    static File primaryReport(File outputDir) {
        return new File(outputDir, "report.txt");
    }

    /** Where it goes instead when the output directory is the very thing that failed. */
    static File fallbackReport(File gameDirectory, String folderName) {
        return new File(gameDirectory, "gametest-" + folderName + "-report.txt");
    }

    /**
     * Removes a verdict an earlier invocation left behind. A stale report sitting where this run's
     * is expected reads as this run's, and a stale {@code RESULT: PASS} is the one artifact that can
     * turn a failing run green &mdash; so failing to remove one is recorded in the verdict itself,
     * not only in the log.
     */
    static void clearStaleReport(File previous, List<String> report) {
        try {
            if (Files.deleteIfExists(previous.toPath())) {
                TestContext.LOGGER.info("[gametest] removed a report from an earlier run: {}", previous);
            }
        } catch (IOException | RuntimeException exception) {
            TestContext.LOGGER.error("[gametest] could not remove the earlier report {}", previous, exception);
            report.add("WARNING: a report from an earlier run could not be removed: " + previous);
        }
    }

    private void record(String status, GameTest.Step step, String note) {
        String line = String.format("[%2d/%2d] %s  %s  (%d ticks)%s",
                stepIndex + 1, test.steps().size(), status, step.description(), ticksInStep,
                note == null ? "" : "  -- " + note);
        report.add(line);
        TestContext.LOGGER.info("[gametest] {}", line);
    }

    /**
     * Ends a run that failed before any step could execute; {@code reason} is its report line. No
     * screenshot accompanies it: there is no test context yet to capture one through.
     */
    private void abort(String reason) {
        report.add("ERROR: " + reason);
        finish(false);
    }

    private void failure() {
        // Best effort: the report and the shutdown matter more than the capture.
        try {
            saveScreenshot("FAILED", outcome -> {
                if (!outcome.saved()) {
                    TestContext.LOGGER.error("[gametest] the failure screenshot was not saved: {}", outcome.detail());
                }
            });
        } catch (Throwable t) {
            TestContext.LOGGER.error("[gametest] could not capture the failure screenshot", t);
        }
        finish(false);
    }

    /**
     * The one terminal path. The shutdown is scheduled before the report is written, so a failing
     * write can never leave an unattended session up with nobody to close it.
     */
    private void finish(boolean pass) {
        finished = true;
        if (!Boolean.getBoolean("glue.gametest.keepOpen")) {
            quitDelay = QUIT_DELAY;
        }
        report.add("RESULT: " + (pass ? "PASS" : "FAIL"));
        TestContext.LOGGER.info("[gametest] {}: {}", testName, pass ? "PASS" : "FAIL");
        writeReport();
    }

    /**
     * The verdict belongs next to the screenshots, but an unattended run has nothing else to read
     * back &mdash; so a run that ended <em>because</em> its output directory is unusable still gets a
     * report, in the game directory, and a filesystem that refuses even that leaves it in the log.
     */
    private void writeReport() {
        File primary = primaryReport(outputDir);
        File written = writeVerdict(primary, fallbackReport(gameDirectory, folderName), report);

        if (written == null) {
            TestContext.LOGGER.error("[gametest] could not write the report anywhere -- it reads:\n{}",
                    String.join("\n", report));
        } else if (!written.equals(primary)) {
            TestContext.LOGGER.warn("[gametest] {} was unusable; verdict written to {}", primary, written);
        }
    }

    /**
     * Writes the verdict to the first destination that accepts it and returns that file, or
     * {@code null} when neither does.
     *
     * <p>The primary is always attempted, even by a run that failed because that directory could not
     * be prepared: writing there replaces whatever an earlier invocation left, and that is the only
     * thing keeping a stale {@code RESULT: PASS} from outliving this run. When even that write fails,
     * the verdict says so itself &mdash; a warning line is appended before the fallback is written, so
     * the report CI ends up reading names the artifact it must not trust.</p>
     */
    static File writeVerdict(File primary, File fallback, List<String> report) {
        if (writeReportTo(primary, report)) return primary;

        report.add("WARNING: " + primary + " could not be written; a report left there is from an earlier run");
        return writeReportTo(fallback, report) ? fallback : null;
    }

    private static boolean writeReportTo(File target, List<String> report) {
        try {
            Files.createDirectories(target.toPath().toAbsolutePath().getParent());
            // Verdict readers match whole lines, so report payloads must never create extra ones.
            List<String> lines = report.stream()
                    .map(line -> line.replace("\r", "\\r").replace("\n", "\\n"))
                    .toList();
            Files.write(target.toPath(), lines);
            return true;
        } catch (IOException | RuntimeException exception) {
            TestContext.LOGGER.error("[gametest] could not write the report to {}", target, exception);
            return false;
        }
    }

    private void saveScreenshot(String label, Consumer<TestContext.ScreenshotOutcome> onDone) {
        Minecraft client = context.client();
        String name = String.format("%02d-%s.png", ++shotCount, label);
        Screenshot.grab(client.gameDirectory, "gametest/" + folderName + "/" + name,
                client.getMainRenderTarget(), 1, message -> onDone.accept(outcomeOf(name, message)));
    }

    /**
     * Vanilla neither throws nor returns anything when a capture fails: it hands the same consumer a
     * different translatable message, from the I/O thread. Reading its key is what separates a PNG on
     * disk from one that never got written.
     */
    static TestContext.ScreenshotOutcome outcomeOf(String name, Component message) {
        boolean saved = message.getContents() instanceof TranslatableContents contents
                && SCREENSHOT_SUCCESS.equals(contents.getKey());
        return new TestContext.ScreenshotOutcome(saved, saved ? name : message.getString());
    }
}
