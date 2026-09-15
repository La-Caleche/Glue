package fr.lacaleche.glue.web.internal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeStartupTest {

    @Test
    void normalizesProgressAndKeepsThePhaseWithItsPercentage() {
        RuntimeStartup startup = new RuntimeStartup();
        startup.begin();
        startup.advance(RuntimeStartup.Stage.DOWNLOADING, 47.9f);
        assertEquals(47, startup.snapshot().percent());
        assertTrue(startup.snapshot().description().contains("47%"));
        startup.advance(RuntimeStartup.Stage.DOWNLOADING, 120);
        assertEquals(100, startup.snapshot().percent());
        startup.advance(RuntimeStartup.Stage.EXTRACTING, -1);
        assertEquals(RuntimeStartup.Stage.EXTRACTING, startup.snapshot().stage());
        assertEquals(-1, startup.snapshot().percent());
        startup.advance(RuntimeStartup.Stage.INITIALIZING, Float.NaN);
        assertEquals(-1, startup.snapshot().percent());
    }

    @Test
    void completedStartupCannotBeRearmedByLateInstallerCallbacks() {
        RuntimeStartup startup = new RuntimeStartup();
        startup.begin();
        startup.ready("Chromium ready");
        RuntimeStartup.Snapshot ready = startup.snapshot();
        startup.advance(RuntimeStartup.Stage.INITIALIZING, 100);
        startup.begin();
        assertSame(ready, startup.snapshot());
        assertFalse(startup.fail(new IllegalStateException("late failure")));
    }

    @Test
    void shutdownWinsOverOutstandingDownloadAndReadiness() {
        RuntimeStartup startup = new RuntimeStartup();
        startup.begin();
        startup.stopping();
        startup.advance(RuntimeStartup.Stage.DOWNLOADING, 50);
        startup.ready("Chromium ready");
        assertFalse(startup.fail(new IllegalStateException("cancelled during shutdown")));
        assertEquals(RuntimeStartup.Stage.STOPPING, startup.snapshot().stage());
        startup.stopped();
        startup.stopping();
        assertEquals(RuntimeStartup.Stage.STOPPED, startup.snapshot().stage());
    }

    @Test
    void readinessAndFailuresExpireInsteadOfLeavingAPermanentOverlay() {
        RuntimeStartup startup = new RuntimeStartup();
        assertEquals(0, startup.snapshot().opacity(0));
        startup.begin();
        assertEquals(1, startup.snapshot().opacity(Long.MAX_VALUE));
        startup.ready("Chromium ready");
        RuntimeStartup.Snapshot ready = startup.snapshot();
        assertEquals(1, ready.opacity(ready.changedAtNanos()));
        assertEquals(0.5f, ready.opacity(ready.changedAtNanos() + 1_750_000_000L));
        assertEquals(0, ready.opacity(ready.changedAtNanos() + 2_000_000_000L));

        RuntimeStartup failed = new RuntimeStartup();
        failed.begin();
        assertTrue(failed.fail(new UnsatisfiedLinkError("native library unavailable")));
        RuntimeStartup.Snapshot error = failed.snapshot();
        assertEquals(RuntimeStartup.Stage.FAILED, error.stage());
        assertEquals(1, error.opacity(error.changedAtNanos() + 7_000_000_000L));
        assertEquals(0, error.opacity(error.changedAtNanos() + 8_000_000_000L));
        failed.advance(RuntimeStartup.Stage.DOWNLOADING, 10);
        assertSame(error, failed.snapshot());
    }
}
