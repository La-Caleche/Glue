package fr.lacaleche.glue.web.internal.browser;

import fr.lacaleche.glue.web.WebMetrics;

/** Client-thread counters; no page scripts or synthetic benchmark probes. */
final class SurfaceMetrics {

    private long uploaded;
    private long lastSequence;
    private long coalesced;
    private long presentedAt;
    private long windowAt = System.nanoTime();
    private long windowPaints;
    private long windowUploads;
    private double captureSum;
    private double stagingSum;
    private double uploadSum;
    private double dirtySum;
    private WebMetrics snapshot = new WebMetrics(0, 0, 0, 0, 0, 0, 0, 0);

    void recordUpload(FrameMailbox.Transfer frame, long uploadNanos) {
        this.captureSum += frame.captureNanos();
        this.stagingSum += frame.stagingNanos();
        this.uploadSum += uploadNanos;
        this.dirtySum += (double) frame.region().width() * frame.region().height() / frame.width() / frame.height();
        this.coalesced += Math.max(0, frame.sequence() - this.lastSequence - 1);
        this.lastSequence = frame.sequence();
        this.presentedAt = frame.capturedAt();
        this.windowUploads++;
        this.uploaded++;
    }

    void sample(long paints) {
        long now = System.nanoTime();
        if (now - this.windowAt < 1_000_000_000L) return;
        double seconds = (now - this.windowAt) / 1_000_000_000.0;
        double denominator = Math.max(1, this.windowUploads) * 1_000_000.0;
        this.snapshot = new WebMetrics((paints - this.windowPaints) / seconds, this.windowUploads / seconds,
                this.captureSum / denominator, this.stagingSum / denominator, this.uploadSum / denominator,
                this.dirtySum * 100 / Math.max(1, this.windowUploads), this.coalesced, this.uploaded);
        this.windowPaints = paints;
        this.windowUploads = 0;
        this.captureSum = this.stagingSum = this.uploadSum = this.dirtySum = 0;
        this.windowAt = now;
    }

    WebMetrics snapshot() {
        return this.snapshot;
    }

    long uploadedFrames() {
        return this.uploaded;
    }

    double frameAgeMs() {
        return this.presentedAt == 0 ? 0 : (System.nanoTime() - this.presentedAt) / 1_000_000.0;
    }
}
