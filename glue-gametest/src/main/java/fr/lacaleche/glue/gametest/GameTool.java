package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * A named, mod-contributed test verb &mdash; the extension point that keeps mod knowledge out of
 * the {@link GameTest} DSL. A mod registers its tools in {@link GameTools} at client init
 * ({@code "ignis:open-vfx"} knows how to load a VFX file into the open editor); tests invoke them
 * by id via {@link GameTest#tool}, so a scripted run drives high-level intents instead of
 * synthetic clicks.
 *
 * <p>{@link #start} is called once per invocation, on the invocation's first tick, and returns the
 * per-run tick that is then polled until it completes &mdash; the same contract as any other step.
 * A tool that finishes immediately returns {@code ctx -> true}; one that must wait (a screen
 * mounting, a file loading) returns a tick polling for its effect. Throwing from either fails the
 * step with the thrown message in the report.</p>
 */
@Environment(EnvType.CLIENT)
@FunctionalInterface
public interface GameTool {

    /** Builds the per-run tick for one invocation; {@code args} come from the test script. */
    GameTest.StepTick start(TestContext ctx, List<String> args) throws Exception;
}
