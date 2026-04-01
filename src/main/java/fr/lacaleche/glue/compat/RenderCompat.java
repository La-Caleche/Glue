package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Compatibility layer for Iris/Oculus shader mods.
 */
public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    /**
     * Registers a pipeline with an Iris program category so shader packs render it correctly.
     */
    public static void assignIrisProgram(RenderPipeline pipeline, String programName) {
        if (!HAS_IRIS) return;

        try {
            IrisProxy.assignPipeline(pipeline, programName);
            Glue.LOGGER.info("[Glue] Registered pipeline '{}' with Iris program '{}'",
                    pipeline.getLocation(), programName);
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Failed to register pipeline with Iris: {}", e.getMessage());
        }
    }

    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }

    /**
     * Returns true during Iris shadow passes. Skip custom rendering to avoid ghost duplicates.
     */
    public static boolean isRenderingShadowPass() {
        return HAS_IRIS && IrisProxy.isRenderingShadowPass();
    }

    /**
     * Runs an action with Iris's pipeline override bypassed.
     * Use for post-processing shaders that should NOT be replaced by shader pack programs.
     */
    public static void withIrisBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        IrisProxy.withBypass(action);
    }
}
