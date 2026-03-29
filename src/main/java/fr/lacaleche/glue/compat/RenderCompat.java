package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Rendering compatibility layer for Iris/Oculus shader mods.
 * <p>
 * Provides two mechanisms for Iris compatibility:
 * <ul>
 *   <li><b>{@link #assignIrisProgram}</b> — Registers a pipeline so Iris applies the shader pack's
 *       rendering to it (for mod-added blocks/entities that should look vanilla under shaders).</li>
 *   <li><b>{@link #withVanillaRendering}</b> — Temporarily switches to vanilla rendering for a
 *       specific draw call (for custom visual effects that should NOT be affected by shader packs).
 *       This uses Iris's {@code VanillaRenderingPipeline} fallback, which is the designed mechanism
 *       for mod rendering that has its own shader code.</li>
 * </ul>
 */
public class RenderCompat {

    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    private static boolean bypassInitialized = false;
    private static boolean bypassReady = false;

    /**
     * Registers a custom pipeline with Iris so it renders correctly with shader packs.
     * <p>
     * This maps the pipeline to an Iris program category (e.g. "TRANSLUCENT") so that
     * Iris's shader pack programs are applied. Use this for rendering that should
     * look like vanilla under shader packs (new block/entity types).
     * <p>
     * Do NOT use this for custom visual effects with their own shader code —
     * use {@link #withVanillaRendering} instead.
     *
     * @param pipeline    The custom pipeline to register
     * @param programName The Iris program category (e.g. "BASIC", "TRANSLUCENT")
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

    /**
     * Executes a rendering callback using vanilla pipeline compilation, bypassing
     * Iris's shader pack overrides.
     * <p>
     * This is the correct approach for custom shader effects that have their own GLSL code
     * and should NOT be replaced by shader pack programs. Internally, it temporarily sets
     * Iris's active pipeline to {@code VanillaRenderingPipeline} — this is Iris's designed
     * fallback mechanism for mod rendering.
     *
     * @param renderAction The rendering code to execute with vanilla pipeline
     */
    public static void withVanillaRendering(Runnable renderAction) {
        if (!HAS_IRIS || !ensureBypassReady()) {
            renderAction.run();
            return;
        }

        try {
            IrisProxy.withVanillaRendering(renderAction);
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Iris vanilla rendering bypass failed, rendering without bypass", e);
            renderAction.run();
        }
    }

    /**
     * Checks if an Iris/Oculus shader pack is currently active.
     */
    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }

    private static boolean ensureBypassReady() {
        if (bypassInitialized) return bypassReady;
        bypassInitialized = true;

        try {
            bypassReady = IrisProxy.initBypass();
            if (bypassReady) {
                Glue.LOGGER.info("[Glue] Iris vanilla rendering bypass ready.");
            }
        } catch (Exception e) {
            Glue.LOGGER.warn("[Glue] Iris bypass setup failed.", e);
            bypassReady = false;
        }
        return bypassReady;
    }
}
