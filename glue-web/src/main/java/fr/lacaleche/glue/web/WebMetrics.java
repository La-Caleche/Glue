package fr.lacaleche.glue.web;

/**
 * One-second delivery sample. Durations are CPU milliseconds, not GPU completion times.
 * Coalesced frames and uploaded frames are cumulative; dirtyPercent is the mean uploaded area.
 */
public record WebMetrics(double paintFps, double uploadFps, double captureMs, double stagingMs,
                         double uploadMs, double dirtyPercent, long coalesced, long frames) {
}
