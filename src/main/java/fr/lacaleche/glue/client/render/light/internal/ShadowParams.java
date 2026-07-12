package fr.lacaleche.glue.client.render.light.internal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.joml.Matrix4f;

/**
 * Everything the deferred pass needs to sample one shadow map.
 *
 * <p>A spot or gobo light has exactly one of these. A point light has six &mdash;
 * one per cube face &mdash; and the deferred pass runs once per face, each run
 * shading only the fragments that fall in that face's 90&deg; frustum.</p>
 *
 * @param textureId     GL depth texture holding <strong>opaque casters only</strong> &mdash; what
 *                      the PCSS shadow test samples. Glass must not appear in it, or a pane
 *                      casts the same solid black shadow as stone.
 * @param tintTextureId GL colour texture of the same render: per-texel <em>transmittance</em>,
 *                      white where the light reaches unimpeded and coloured where it passed
 *                      through stained glass. This is what makes glass tint light instead
 *                      of blocking it. {@code -1} when this map has no translucent casters.
 * @param tintDepthId   GL depth texture holding opaque <strong>plus translucent</strong> casters
 *                      &mdash; the {@code shadowtex0} to {@code textureId}'s {@code shadowtex1},
 *                      in shaderpack terms. A receiver that is shadowed here but lit in
 *                      {@code textureId} has a pane (and only a pane) between it and the
 *                      light, so it takes the tint. A plain depth compare: no packed
 *                      distances, no quantisation. {@code -1} when no translucent casters.
 * @param lightViewProj <em>light-relative</em> world -&gt; shadow-map clip. Light-relative,
 *                      not camera-relative, so the map survives camera movement and can
 *                      be cached; the shader converts by subtracting the light position.
 * @param mapSize       shadow map resolution (square)
 * @param near          shadow frustum near plane
 * @param far           shadow frustum far plane
 * @param focalY        {@code lightProj.m11()} &mdash; converts a world size to an NDC size
 *                      at a given depth, which is what lets the shader derive its bias and
 *                      PCSS radii from the map's resolution instead of hand-tuned constants
 * @param lightSize     emitter radius in blocks; drives the penumbra width
 * @param faceAxis      cube face index 0..5 (+X, -X, +Y, -Y, +Z, -Z), or -1 for a spot.
 *                      The shader discards fragments whose dominant axis from the light
 *                      is not this face, so the six passes tile the sphere without overlap.
 */
@Environment(EnvType.CLIENT)
public record ShadowParams(
        int textureId,
        int tintTextureId,
        int tintDepthId,
        Matrix4f lightViewProj,
        float mapSize,
        float near,
        float far,
        float focalY,
        float lightSize,
        int faceAxis
) {
}
