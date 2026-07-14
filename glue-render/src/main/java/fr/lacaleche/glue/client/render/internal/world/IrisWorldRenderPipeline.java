package fr.lacaleche.glue.client.render.internal.world;

import fr.lacaleche.glue.client.render.internal.material.TerrainMaterialBuffer;
import fr.lacaleche.glue.compat.RenderCompat;

/**
 * World frame published while an Iris shaderpack is active: Minecraft's main render target,
 * the captured level matrices, and no material capture.
 *
 * <p>This is the empirically-working approximation, not a proven-coherent frame. It does not
 * guarantee that:</p>
 * <ul>
 *   <li>the color it hands out is untouched scene color — the pack may already have applied
 *       exposure, bloom, or tonemapping, so it is declared {@code SRGB} / {@code FINAL_COLOR}
 *       by convention rather than by proof;</li>
 *   <li>the main depth matches the pack's own scene depth — a pack may render its geometry
 *       into targets whose depth differs from Minecraft's main depth attachment;</li>
 *   <li>a later shaderpack stage will preserve whatever a consumer composites here.</li>
 * </ul>
 *
 * <p>No material frame is published: an active pack owns an arbitrary colortex layout, so there
 * is nothing to capture terrain material from.</p>
 */
final class IrisWorldRenderPipeline extends AbstractMainTargetWorldPipeline {

    @Override
    public String id() {
        return "glue:iris_final_color";
    }

    @Override
    public boolean isApplicable() {
        return RenderCompat.isIrisShaderEnabled();
    }

    @Override
    public void beginFrame(long sequence, boolean materialRequested) {
        super.beginFrame(sequence);
        TerrainMaterialBuffer.cancelFrame(sequence);
    }
}
