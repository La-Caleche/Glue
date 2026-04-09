package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;

public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    public static void assignIrisProgram(RenderPipeline pipeline, String programName) {
        if (!HAS_IRIS) return;

        try {
            IrisProxy.assignPipeline(pipeline, programName);
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to register pipeline with Iris: {}", e.getMessage());
        }
    }

    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }

    public static boolean isRenderingShadowPass() {
        return HAS_IRIS && IrisProxy.isRenderingShadowPass();
    }

    public static boolean isIrisBypassing() {
        return HAS_IRIS && IrisProxy.isBypassing();
    }

    public static void withIrisBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        IrisProxy.withBypass(action);
    }

    public static void withIrisFullBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        IrisProxy.withFullBypass(action);
    }

    public static int getIrisMainDepthGlId() {
        if (!HAS_IRIS) return -1;
        return IrisProxy.getMainDepthGlId();
    }

    public static int getIrisSceneDepthGlId() {
        if (!HAS_IRIS) return -1;
        return IrisProxy.getSceneDepthGlId();
    }

    public static Object[] getIrisRenderTargetArray() {
        if (!HAS_IRIS) return null;
        return IrisProxy.getIrisRenderTargetArray();
    }

    public static int[] getIrisTargetTextures(Object target, String name) {
        if (!HAS_IRIS) return null;
        return IrisProxy.getIrisTargetTextures(target, name);
    }
}
