package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Holds a step open until the client has drawn a number of world frames. A step that changes what
 * the renderer does (rebuilding a shader pipeline, mounting a screen) is only really finished once
 * the new state has been drawn, and drawn frames are not client ticks: after a long synchronous
 * reload Minecraft catches up several ticks without rendering, so a tick-counted wait can hand the
 * next step a picture that was never redrawn.
 *
 * <p>Each instance settles once: the first {@link #settled(int)} call takes the current count as
 * its baseline, and the step completes when the count has advanced past it by the required number
 * of frames. The caller feeds {@link TestContext#renderedWorldFrames()}.</p>
 */
@Environment(EnvType.CLIENT)
final class FrameSettle {

    private final int requiredFrames;
    private int baseline = -1;

    FrameSettle(int requiredFrames) {
        if (requiredFrames < 0) {
            throw new IllegalArgumentException("requiredFrames must not be negative: " + requiredFrames);
        }
        this.requiredFrames = requiredFrames;
    }

    boolean settled(int renderedFrames) {
        if (baseline < 0) {
            baseline = renderedFrames;
        }
        return renderedFrames - baseline >= requiredFrames;
    }
}
