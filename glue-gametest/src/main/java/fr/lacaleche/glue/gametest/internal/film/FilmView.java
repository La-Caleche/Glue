package fr.lacaleche.glue.gametest.internal.film;

import fr.lacaleche.glue.gametest.CameraPose;
import net.minecraft.client.DeltaTracker;

/**
 * What the film camera shows while a {@code FilmRecorder} runs: read by the mixins on the client
 * thread, written by the recorder on the client thread. Not API.
 */
public final class FilmView {

    private static CameraPose pose;
    private static DeltaTracker delta;

    private FilmView() {
    }

    /** The forced pose, or null outside a recording. */
    public static CameraPose pose() {
        return pose;
    }

    /** The frame's fixed time step, or null outside a recording. */
    public static DeltaTracker delta() {
        return delta;
    }

    public static void show(CameraPose pose, float partialTick, float ticksPerFrame) {
        FilmView.pose = pose;
        FilmView.delta = new FixedDelta(partialTick, ticksPerFrame);
    }

    public static void clear() {
        pose = null;
        delta = null;
    }

    private record FixedDelta(float partialTick, float ticksPerFrame) implements DeltaTracker {

        @Override
        public float getGameTimeDeltaTicks() {
            return this.ticksPerFrame;
        }

        @Override
        public float getGameTimeDeltaPartialTick(boolean ignoreFrozenGame) {
            return this.partialTick;
        }

        @Override
        public float getRealtimeDeltaTicks() {
            return this.ticksPerFrame;
        }
    }
}
