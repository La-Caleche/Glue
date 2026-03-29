package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Iris API proxy. Isolated so Iris classes are only loaded when Iris is present.
 * <p>
 * Uses two mechanisms:
 * <ul>
 *   <li>{@code IrisApi.assignPipeline()} — official public API for program assignment</li>
 *   <li>{@code PipelineManager.pipeline} field swap — for vanilla rendering bypass
 *       (no public API exists for this; uses the standard community approach)</li>
 * </ul>
 */
class IrisProxy {

    private IrisProxy() {}

    // Reflection for vanilla rendering bypass
    private static Method getPipelineManagerMethod;
    private static Field pipelineField;
    private static Object cachedVanillaPipeline;

    static boolean isIrisShaderEnabled() {
        return IrisApi.getInstance().isShaderPackInUse();
    }

    /**
     * Registers a pipeline with Iris using the official API.
     */
    static void assignPipeline(RenderPipeline pipeline, String programName) {
        IrisProgram program = IrisProgram.valueOf(programName);
        IrisApi.getInstance().assignPipeline(pipeline, program);
    }

    /**
     * Initializes the reflection handles needed for the vanilla rendering bypass.
     * This is only needed for custom shaders that should bypass shader pack overrides.
     */
    static boolean initBypass() {
        try {
            Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris");
            getPipelineManagerMethod = irisClass.getMethod("getPipelineManager");

            Class<?> pipelineManagerClass = Class.forName("net.irisshaders.iris.pipeline.PipelineManager");
            pipelineField = pipelineManagerClass.getDeclaredField("pipeline");
            pipelineField.setAccessible(true);

            Class<?> vanillaPipelineClass = Class.forName("net.irisshaders.iris.pipeline.VanillaRenderingPipeline");
            cachedVanillaPipeline = vanillaPipelineClass.getDeclaredConstructor().newInstance();

            return true;
        } catch (Exception e) {
            throw new RuntimeException("Iris bypass reflection setup failed", e);
        }
    }

    /**
     * Executes a render action with Iris's pipeline temporarily set to VanillaRenderingPipeline.
     * This causes Iris's redirectIrisProgram handler to fall through to vanilla compilation.
     */
    static void withVanillaRendering(Runnable renderAction) throws Exception {
        Object pm = getPipelineManagerMethod.invoke(null);
        Object prevPipeline = pipelineField.get(pm);

        // If already vanilla, just run directly
        if (cachedVanillaPipeline.getClass().isInstance(prevPipeline)) {
            renderAction.run();
            return;
        }

        // Temporarily switch to vanilla
        pipelineField.set(pm, cachedVanillaPipeline);
        try {
            renderAction.run();
        } finally {
            // Always restore, even on exception
            pipelineField.set(pm, prevPipeline);
        }
    }
}
