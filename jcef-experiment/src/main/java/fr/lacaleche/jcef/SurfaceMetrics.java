package fr.lacaleche.jcef;

/** Client-thread delivery counters, independent of texture ownership and browser startup. */
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
    private double conversionSum;
    private double uploadSum;
    private double dirtySum;
    private int presentedProbe;
    private double probePaintMs;
    private double probeUploadMs;
    private double probeAckMs;
    private CefSurface.Metrics snapshot = new CefSurface.Metrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    void recordUpload(FrameMailbox.Transfer frame, SurfaceRenderer.Upload timing, CefView.ProbePaint probe,
                      int acknowledgedProbe, long acknowledgedAt) {
        this.captureSum += frame.captureNanos();
        this.stagingSum += frame.stagingNanos();
        this.conversionSum += timing.conversionNanos();
        this.uploadSum += timing.uploadNanos();
        this.dirtySum += (double) frame.region().width() * frame.region().height() / frame.width() / frame.height();
        this.coalesced += Math.max(0, frame.sequence() - this.lastSequence - 1);
        this.lastSequence = frame.sequence();
        this.presentedAt = frame.capturedAt();
        this.windowUploads++;
        this.uploaded++;

        if (probe == null || probe.token() == this.presentedProbe || frame.sequence() < probe.sequence()) return;
        this.probePaintMs = (probe.paintedAt() - probe.sentAt()) / 1_000_000.0;
        this.probeUploadMs = (System.nanoTime() - probe.sentAt()) / 1_000_000.0;
        this.presentedProbe = probe.token();
        this.probeAckMs = acknowledgedProbe == probe.token() ? (acknowledgedAt - probe.sentAt()) / 1_000_000.0 : 0;
    }

    void sample(long paints) {
        long now = System.nanoTime();
        if (now - this.windowAt < 1_000_000_000L) return;
        double seconds = (now - this.windowAt) / 1_000_000_000.0;
        double denominator = Math.max(1, this.windowUploads) * 1_000_000.0;
        this.snapshot = new CefSurface.Metrics(
                (paints - this.windowPaints) / seconds, this.windowUploads / seconds,
                this.captureSum / denominator, this.stagingSum / denominator,
                this.conversionSum / denominator, this.uploadSum / denominator,
                this.dirtySum * 100 / Math.max(1, this.windowUploads), this.coalesced,
                this.probePaintMs, this.probeUploadMs, this.uploaded, this.probeAckMs);
        this.windowPaints = paints;
        this.windowUploads = 0;
        this.captureSum = this.stagingSum = this.conversionSum = this.uploadSum = this.dirtySum = 0;
        this.windowAt = now;
    }

    CefSurface.Metrics snapshot() {
        return this.snapshot;
    }

    long uploadedFrames() {
        return this.uploaded;
    }

    int completedProbe() {
        return this.presentedProbe;
    }

    double frameAgeMs() {
        return this.presentedAt == 0 ? 0 : (System.nanoTime() - this.presentedAt) / 1_000_000.0;
    }
}
