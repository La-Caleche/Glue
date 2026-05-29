package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;
import net.irisshaders.iris.api.v0.IrisApi;
import net.irisshaders.iris.api.v0.IrisProgram;
import net.irisshaders.iris.vertices.ImmediateState;

public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    private static final String IRIS_CLASS = "net.irisshaders.iris.Iris";

    // Per-frame cache for Iris depth IDs. -2 = not yet resolved this frame, -1 = unavailable.
    private static int cachedIrisMainDepthId = -2;
    private static int cachedIrisSceneDepthId = -2;

    /**
     * Must be called at the start of each frame (e.g. WorldRenderEvents.START) to invalidate the cache.
     */
    public static void resetFrameCache() {
        cachedIrisMainDepthId = -2;
        cachedIrisSceneDepthId = -2;
    }

    public static void assignIrisProgram(RenderPipeline pipeline, String programName) {
        if (!HAS_IRIS) return;
        try {
            IrisApi.getInstance().assignPipeline(pipeline, IrisProgram.valueOf(programName));
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to register pipeline with Iris: {}", e.getMessage());
        }
    }

    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisApi.getInstance().isShaderPackInUse();
    }

    public static boolean isRenderingShadowPass() {
        return HAS_IRIS && IrisApi.getInstance().isRenderingShadowPass();
    }

    public static boolean isIrisBypassing() {
        return HAS_IRIS && ImmediateState.bypass;
    }

    public static void withIrisBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        boolean wasBypassed = ImmediateState.bypass;
        ImmediateState.bypass = true;
        try {
            action.run();
        } finally {
            ImmediateState.bypass = wasBypassed;
        }
    }

    public static void withIrisFullBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        boolean wasBypassed = ImmediateState.bypass;
        boolean wasSafe = ImmediateState.safeToMultiply;
        ImmediateState.bypass = true;
        ImmediateState.safeToMultiply = true;
        try {
            action.run();
        } finally {
            ImmediateState.bypass = wasBypassed;
            ImmediateState.safeToMultiply = wasSafe;
        }
    }

    private static Object getIrisPipeline() {
        Object mgr = ModCompatManager.getSingleton("iris", IRIS_CLASS, "getPipelineManager", false);
        if (mgr == null) return null;
        return ModCompatManager.invokeRuntime(mgr, "getPipelineNullable", false, new Class[0], new Object[0], Object.class, null);
    }

    private static Object getRenderTargets() {
        Object pipeline = getIrisPipeline();
        if (pipeline == null) return null;
        return ModCompatManager.getFieldValue(pipeline, pipeline.getClass().getName(), "renderTargets", true, Object.class, null);
    }

    public static int getIrisMainDepthGlId() {
        if (!HAS_IRIS) return -1;
        if (cachedIrisMainDepthId != -2) return cachedIrisMainDepthId;
        Object rts = getRenderTargets();
        if (rts == null) return cachedIrisMainDepthId = -1;
        Object depthTex = ModCompatManager.invokeRuntime(rts, "getDepthTexture", false, new Class[0], new Object[0], Object.class, null);
        if (depthTex == null) return cachedIrisMainDepthId = -1;
        return cachedIrisMainDepthId = ModCompatManager.invokeRuntime(depthTex, "glId", false, new Class[0], new Object[0], Integer.class, -1);
    }

    public static int getIrisSceneDepthGlId() {
        if (!HAS_IRIS) return -1;
        if (cachedIrisSceneDepthId != -2) return cachedIrisSceneDepthId;
        Object rts = getRenderTargets();
        if (rts == null) return cachedIrisSceneDepthId = -1;
        Object depthTex = ModCompatManager.invokeRuntime(rts, "getDepthTextureNoHand", false, new Class[0], new Object[0], Object.class, null);
        if (depthTex == null) return cachedIrisSceneDepthId = -1;
        return cachedIrisSceneDepthId = ModCompatManager.invokeRuntime(depthTex, "glId", false, new Class[0], new Object[0], Integer.class, -1);
    }

    public static Object[] getIrisRenderTargetArray() {
        if (!HAS_IRIS) return null;
        Object rts = getRenderTargets();
        if (rts == null) return null;
        return ModCompatManager.getFieldValue(rts, rts.getClass().getName(), "targets", true, Object[].class, null);
    }

    public static int[] getIrisTargetTextures(Object target, String name) {
        if (!HAS_IRIS || target == null) return null;
        try {
            String className = target.getClass().getName();
            Integer mainId = ModCompatManager.getFieldValue(target, className, "mainTexture", true, Integer.class, null);
            Integer altId = ModCompatManager.getFieldValue(target, className, "altTexture", true, Integer.class, null);
            Integer width = ModCompatManager.getFieldValue(target, className, "width", true, Integer.class, null);
            Integer height = ModCompatManager.getFieldValue(target, className, "height", true, Integer.class, null);

            if (mainId == null || altId == null || width == null || height == null) return null;
            return new int[]{mainId, altId, width, height};
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to read Iris target: {}", name, e);
            return null;
        }
    }
}
