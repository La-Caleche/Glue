package fr.lacaleche.glue.web.internal.browser;

import java.util.Objects;

/** Atomic progress snapshots shared by the installer, CEF callbacks and the GUI thread. */
final class RuntimeStartup {

    private volatile Snapshot snapshot = new Snapshot(Stage.NOT_STARTED, -1, "Not started", 0);

    Snapshot snapshot() {
        return this.snapshot;
    }

    synchronized void begin() {
        if (this.snapshot.stage() != Stage.NOT_STARTED) return;
        this.set(Stage.CHECKING, -1, "Checking Glue resources");
    }

    synchronized void advance(Stage stage, float percent) {
        Objects.requireNonNull(stage, "stage");
        if (!stage.isActive()) throw new IllegalArgumentException("Expected an active startup stage");
        if (!this.snapshot.stage().isActive()) return;
        int normalized = !Float.isFinite(percent) || percent < 0 ? -1 : Math.clamp((int) percent, 0, 100);
        if (this.snapshot.stage() == stage && this.snapshot.percent() == normalized) return;
        String detail = stage.description + (normalized < 0 ? "" : " (" + normalized + "%)");
        this.set(stage, normalized, detail);
    }

    synchronized void ready(String description) {
        if (this.snapshot.stage().isActive()) this.set(Stage.READY, 100, description);
    }

    synchronized boolean fail(Throwable failure) {
        if (!this.snapshot.stage().isActive()) return false;
        this.set(Stage.FAILED, -1, "Startup failed: " + failure);
        return true;
    }

    synchronized void stopping() {
        if (this.snapshot.stage() != Stage.STOPPED) this.set(Stage.STOPPING, -1, "Stopping");
    }

    synchronized void stopped() {
        this.set(Stage.STOPPED, -1, "Stopped");
    }

    private void set(Stage stage, int percent, String description) {
        this.snapshot = new Snapshot(stage, percent, description, System.nanoTime());
    }

    enum Stage {
        NOT_STARTED("Not started"),
        CHECKING("Checking Glue resources"),
        DOWNLOADING("Downloading Glue resources"),
        EXTRACTING("Extracting Glue resources"),
        INSTALLING("Installing Glue resources"),
        INITIALIZING("Starting Chromium"),
        READY("Glue resources ready"),
        FAILED("Could not load Glue resources"),
        STOPPING("Stopping"),
        STOPPED("Stopped");

        private final String description;

        Stage(String description) {
            this.description = description;
        }

        boolean isActive() {
            return switch (this) {
                case CHECKING, DOWNLOADING, EXTRACTING, INSTALLING, INITIALIZING -> true;
                default -> false;
            };
        }
    }

    record Snapshot(Stage stage, int percent, String description, long changedAtNanos) {

        /** Completion fades after two seconds; failures stay readable for eight seconds. */
        float opacity(long nowNanos) {
            if (this.stage.isActive()) return 1;
            long duration = switch (this.stage) {
                case READY -> 2_000_000_000L;
                case FAILED -> 8_000_000_000L;
                default -> 0;
            };
            if (duration == 0) return 0;
            long elapsed = Math.max(0, nowNanos - this.changedAtNanos);
            return (float) Math.clamp((duration - elapsed) / 500_000_000.0, 0, 1);
        }
    }
}
