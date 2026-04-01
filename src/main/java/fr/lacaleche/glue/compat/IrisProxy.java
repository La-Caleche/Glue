package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.vertices.ImmediateState;

/**
 * Isolated Iris API calls. Only loaded when Iris is present.
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
        IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.valueOf(programName));
    }

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
