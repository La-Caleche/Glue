package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.vertices.ImmediateState;

/**
 * Iris API proxy. Isolated so Iris classes are only loaded when Iris is present.
 * <p>
 * Uses both the official {@code IrisApi} public interface and Iris's
 * {@code ImmediateState.bypass} flag for pipeline override bypass.
 */
class IrisProxy {

    private IrisProxy() {}

    static boolean isIrisShaderEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    static boolean isRenderingShadowPass() {
        return IrisApi.getInstance().isRenderingShadowPass();
    }

    static void assignPipeline(RenderPipeline pipeline, String programName) {
        IrisProgram program = IrisProgram.valueOf(programName);
        IrisApi.getInstance().assignPipeline(pipeline, program);
    }

    /**
     * Executes an action with Iris's pipeline override bypass enabled.
     * <p>
     * Sets {@code ImmediateState.bypass = true} which causes Iris's
     * {@code redirectIrisProgram} mixin to skip shader pack overrides,
     * letting vanilla pipeline compilation proceed normally.
     * <p>
     * This is the correct mechanism for post-processing shaders and other
     * pipelines that should NOT be replaced by shader pack programs.
     */
    static void withBypass(Runnable action) {
        boolean wasBypassed = ImmediateState.bypass;
        ImmediateState.bypass = true;
        try {
            action.run();
        } finally {
            ImmediateState.bypass = wasBypassed;
        }
    }
}
