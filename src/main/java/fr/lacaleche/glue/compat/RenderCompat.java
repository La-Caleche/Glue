package fr.lacaleche.glue.compat;

import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.Nullable;

public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    private static boolean pipelineBypassInitialized = false;
    private static boolean pipelineBypassReady = false;

    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }

    public static class IrisSavedState {
        Object pipelineManager;
        Object previousPipeline;
        Object dimensionKey;
        Object previousMapEntry;
        boolean hadMapEntry;
    }

    private static void initPipelineBypass() {
        if (pipelineBypassInitialized) return;
        pipelineBypassInitialized = true;

        if (!HAS_IRIS) return;

        try {
            pipelineBypassReady = IrisProxy.initReflection();
            Glue.LOGGER.info("[Glue] Iris pipeline bypass ready.");
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Iris detected but pipeline bypass setup failed.", e);
            pipelineBypassReady = false;
        }
    }

    public static boolean isPipelineBypassReady() {
        initPipelineBypass();
        return pipelineBypassReady;
    }

    @Nullable
    public static IrisSavedState bypassIrisPipeline() {
        initPipelineBypass();
        if (!pipelineBypassReady) return null;

        try {
            return IrisProxy.bypassPipeline();
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to bypass Iris pipeline", e);
            return null;
        }
    }

    public static void restoreIrisPipeline(@Nullable IrisSavedState state) {
        if (state == null || !pipelineBypassReady) return;

        try {
            IrisProxy.restorePipeline(state);
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to restore Iris pipeline", e);
        }
    }
}
