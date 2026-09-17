package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.List;

/**
 * Built-in {@link GameTool} toggling the active Iris shaderpack, registered under {@link #ID} at
 * client init: {@code test.tool(IrisShadersTool.ID, "true")}. One argument, {@code "true"} or
 * {@code "false"}. The step applies the toggle, verifies Iris reports the requested state, then
 * holds until the rebuilt pipeline has drawn {@value #SETTLE_FRAMES} world frames &mdash; call
 * sites need no manual settle wait after it. When the requested state already holds, the step
 * completes immediately without settling.
 *
 * <p>The settle counts drawn frames rather than client ticks because a shaderpack reload blocks
 * the client thread: Minecraft then catches up the ticks it missed without rendering, and a
 * tick-counted settle would let the next step screenshot a pipeline that has not drawn once. The
 * step consequently needs a rendering world &mdash; with nothing drawing, it fails on its
 * timeout.</p>
 *
 * <p>Iris stays optional for this module (compileOnly): the tool resolves Iris classes only when
 * invoked, and invoking it without Iris installed fails the step with a clear message.</p>
 */
@Environment(EnvType.CLIENT)
public final class IrisShadersTool implements GameTool {

    public static final String ID = "glue-gametest:iris-shaders";

    /** World frames the rebuilt Iris pipeline must draw before the next step runs. */
    static final int SETTLE_FRAMES = 10;

    @Override
    public GameTest.StepTick start(TestContext context, List<String> arguments) {
        return new ToggleTick(parseEnabled(arguments));
    }

    private static boolean parseEnabled(List<String> arguments) {
        if (arguments.size() != 1 || !List.of("true", "false").contains(arguments.getFirst())) {
            throw new IllegalArgumentException(
                    "the iris-shaders tool expects one argument, true or false, got " + arguments);
        }
        return Boolean.parseBoolean(arguments.getFirst());
    }

    private static final class ToggleTick implements GameTest.StepTick {

        private final boolean enabled;
        private final FrameSettle settle = new FrameSettle(SETTLE_FRAMES);
        private boolean applied;

        private ToggleTick(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        public boolean tick(TestContext context) {
            if (!IrisHooks.loaded()) {
                throw new IllegalStateException("the iris-shaders tool requires Iris at runtime");
            }
            if (!applied) {
                applied = true;
                if (IrisHooks.shaderPackInUse() == enabled) return true;

                IrisHooks.setShadersEnabled(enabled);
            }
            if (IrisHooks.shaderPackInUse() != enabled) return false;

            return settle.settled(context.renderedWorldFrames());
        }
    }
}
