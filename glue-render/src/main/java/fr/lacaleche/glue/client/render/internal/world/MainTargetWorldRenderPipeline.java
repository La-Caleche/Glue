package fr.lacaleche.glue.client.render.internal.world;

import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import fr.lacaleche.glue.compat.RenderCompat;
import net.minecraft.client.Minecraft;

/**
 * Minecraft's main render target published as the world frame, with an optional terrain
 * material capture. Applicable whenever no Iris shaderpack owns the frame; the material
 * capture implementation (Vanilla replay or Sodium MRT) is chosen by
 * {@link TerrainMaterialBuffer}.
 *
 * <p>Under Fabulous graphics the frame is still published, but no material is captured: the
 * transparency post-chain composites translucents from a separate target, so the material
 * depth would not match the main scene depth. Consumers fall back to the scene-color estimate
 * and glass is not lit — the same degradation the pre-pipeline renderer accepted.</p>
 */
final class MainTargetWorldRenderPipeline extends AbstractMainTargetWorldPipeline {

    @Override
    public String id() {
        return "glue:main_target";
    }

    @Override
    public boolean isApplicable() {
        return !RenderCompat.isIrisShaderEnabled();
    }

    @Override
    public void beginFrame(long sequence, boolean materialRequested) {
        super.beginFrame(sequence);
        boolean material = materialRequested && !Minecraft.useShaderTransparency();
        TerrainMaterialBuffer.beginFrame(sequence, material);
    }
}
