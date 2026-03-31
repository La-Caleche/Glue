package fr.lacaleche.glue.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import fr.lacaleche.glue.Glue;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Rendering compatibility layer for Iris/Oculus shader mods.
 * <p>
 * Provides:
 * <ul>
 *   <li><b>{@link #assignIrisProgram}</b> — Registers a pipeline with an Iris program category
 *       so shader packs apply correct rendering (for mod-added blocks/entities).</li>
 *   <li><b>{@link #isIrisShaderEnabled()}</b> — Checks if a shader pack is active.</li>
 *   <li><b>{@link #isRenderingShadowPass()}</b> — Detects shadow passes for draw deduplication.</li>
 * </ul>
 * <p>
 * Custom visual effects with their own GLSL code bypass Iris entirely via
 * {@link fr.lacaleche.glue.client.shader.GlDirectRenderer} (raw OpenGL).
 */
public class RenderCompat {

    /** Whether Iris or Oculus is loaded in the mod environment. */
    public static final boolean HAS_IRIS = FabricLoader.getInstance().isModLoaded("iris")
            || FabricLoader.getInstance().isModLoaded("oculus");

    /**
     * Registers a custom pipeline with Iris so shader packs render it correctly.
     * <p>
     * Maps the pipeline to an Iris program category (e.g. "TRANSLUCENT") so that
     * shader pack programs are applied. Use for rendering that should look correct
     * under shader packs (new block/entity types).
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
     * Checks if an Iris/Oculus shader pack is currently active.
     */
    public static boolean isIrisShaderEnabled() {
        return HAS_IRIS && IrisProxy.isIrisShaderEnabled();
    }

    /**
     * Checks if Iris is currently rendering the shadow pass.
     * <p>
     * During shadow passes, block entities are re-rendered from the light's perspective
     * to generate shadow maps. Custom rendering should skip shadow passes to avoid
     * ghost duplicates at incorrect positions.
     *
     * @return true if in shadow pass, false otherwise (or if Iris is not loaded)
     */
    public static boolean isRenderingShadowPass() {
        return HAS_IRIS && IrisProxy.isRenderingShadowPass();
    }

    /**
     * Executes a rendering action with Iris's pipeline override bypassed.
     * <p>
     * When Iris shaders are active, custom pipelines (e.g. post-processing shaders)
     * would be intercepted by Iris's {@code redirectIrisProgram} mixin, causing
     * "Missing program" errors. This method temporarily sets Iris's bypass flag
     * so the vanilla pipeline compilation proceeds normally.
     * <p>
     * Safe to call without Iris — the action runs directly.
     *
     * @param action The rendering code to execute with bypass enabled
     */
    public static void withIrisBypass(Runnable action) {
        if (!HAS_IRIS) {
            action.run();
            return;
        }
        IrisProxy.withBypass(action);
    }
}
