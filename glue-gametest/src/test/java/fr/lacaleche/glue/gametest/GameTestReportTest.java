package fr.lacaleche.glue.gametest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where an unattended run's verdict ends up, and what happens to one left by an earlier run. A stale
 * {@code RESULT: PASS} is the only artifact that can turn a failing run green, so the destination
 * policy is exercised here rather than left to a live client.
 */
class GameTestReportTest {

    private static final List<String> PASSING = List.of("[ 1/ 1] PASS  a step  (1 ticks)", "RESULT: PASS");

    @TempDir
    File gameDirectory;

    @Test
    void theVerdictLandsBesideTheScreenshots() throws IOException {
        File outputDir = new File(gameDirectory, "screenshots/gametest/mymod_smoke");
        File primary = GameTestRunner.primaryReport(outputDir);

        File written = GameTestRunner.writeVerdict(primary, fallback(), new ArrayList<>(PASSING));

        assertEquals(primary, written);
        assertEquals(PASSING, Files.readAllLines(primary.toPath()));
    }

    @Test
    void aVerdictFromAnEarlierRunIsReplaced() throws IOException {
        File outputDir = new File(gameDirectory, "screenshots/gametest/mymod_smoke");
        File primary = GameTestRunner.primaryReport(outputDir);
        Files.createDirectories(outputDir.toPath());
        Files.write(primary.toPath(), List.of("RESULT: PASS"));

        List<String> failing = new ArrayList<>(List.of("ERROR: no such test", "RESULT: FAIL"));
        GameTestRunner.writeVerdict(primary, fallback(), failing);

        assertEquals(failing, Files.readAllLines(primary.toPath()));
    }

    @Test
    void multilineFailureTextCannotForgeAPassingVerdict() throws IOException {
        File outputDir = new File(gameDirectory, "screenshots/gametest/mymod_smoke");
        File primary = GameTestRunner.primaryReport(outputDir);
        List<String> failing = List.of(
                "[ 1/ 1] FAIL  assertion  (1 ticks)  -- first line\r\nRESULT: PASS\nlast line",
                "RESULT: FAIL"
        );

        GameTestRunner.writeVerdict(primary, fallback(), new ArrayList<>(failing));

        List<String> written = Files.readAllLines(primary.toPath());
        assertEquals(2, written.size(), written.toString());
        assertFalse(written.contains("RESULT: PASS"), written.toString());
        assertEquals("RESULT: FAIL", written.getLast());
    }

    @Test
    void anUnwritablePrimaryFallsBackAndSaysSo() throws IOException {
        File primary = unwritable("report.txt");

        List<String> report = new ArrayList<>(PASSING);
        File written = GameTestRunner.writeVerdict(primary, fallback(), report);

        assertEquals(fallback(), written);
        List<String> lines = Files.readAllLines(fallback().toPath());
        assertTrue(lines.getLast().startsWith("WARNING: "), lines.toString());
        assertTrue(lines.getLast().contains(primary.toString()), lines.toString());
    }

    @Test
    void aVerdictWithNowhereToGoReportsNothing() throws IOException {
        File primary = unwritable("report.txt");
        File fallback = unwritable("gametest-mymod_smoke-report.txt");

        assertNull(GameTestRunner.writeVerdict(primary, fallback, new ArrayList<>(PASSING)));
    }

    @Test
    void aReportFromAnEarlierRunIsRemoved() throws IOException {
        File stale = fallback();
        Files.write(stale.toPath(), List.of("RESULT: PASS"));

        List<String> report = new ArrayList<>();
        GameTestRunner.clearStaleReport(stale, report);

        assertFalse(stale.exists());
        assertEquals(List.of(), report);
    }

    @Test
    void aStaleReportThatSurvivesIsRecordedInTheVerdict() throws IOException {
        File undeletable = unwritable("gametest-mymod_smoke-report.txt");

        List<String> report = new ArrayList<>();
        GameTestRunner.clearStaleReport(undeletable, report);

        assertEquals(1, report.size(), report.toString());
        assertTrue(report.getFirst().startsWith("WARNING: "), report.toString());
    }

    @Test
    void theTwoDestinationsAreNamedFromTheTest() {
        File outputDir = new File(gameDirectory, "screenshots/gametest/mymod_smoke");

        assertEquals(new File(outputDir, "report.txt"), GameTestRunner.primaryReport(outputDir));
        assertEquals(new File(gameDirectory, "gametest-mymod_smoke-report.txt"),
                GameTestRunner.fallbackReport(gameDirectory, "mymod_smoke"));
    }

    private File fallback() {
        return GameTestRunner.fallbackReport(gameDirectory, "mymod_smoke");
    }

    /** A non-empty directory in the report's place: it can be neither written nor deleted, on any OS. */
    private File unwritable(String name) throws IOException {
        File blocked = new File(gameDirectory, name);
        Files.createDirectories(blocked.toPath());
        Files.write(new File(blocked, "occupant").toPath(), List.of("in the way"));
        return blocked;
    }
}
