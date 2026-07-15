package fr.lacaleche.glue.client.render.light.internal.shadow;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.lacaleche.glue.client.shader.GluePipeline;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

/**
 * The two pipelines a shadow map is baked with.
 *
 * <p>A {@code TextureTarget} always allocates a colour attachment whether you want one
 * or not, and the shadow maps used to throw theirs away. It now carries the light's
 * <strong>transmittance</strong>: how much of each colour channel survives the trip
 * from the light to that texel. So:</p>
 *
 * <ul>
 *   <li><b>{@link #depth()}</b> &mdash; opaque casters. Writes depth, no colour. The
 *       alpha cutout is what gives leaves and grates lacy shadows rather than solid
 *       cubes. Colour writes are <em>off</em> because otherwise every texel would be
 *       stamped with the albedo of whatever block sits at that depth, and the light
 *       would come out tinted by its own occluders.</li>
 *   <li><b>{@link #tintColor()}</b> &mdash; translucent casters (glass, stained glass,
 *       ice). Every pane on the ray multiplies its transmittance over the white-cleared
 *       buffer (merged colours through stacked panes), depth-tested against the opaque
 *       depth already in the buffer but never writing it.</li>
 *   <li><b>{@link #tintDepth()}</b> &mdash; the same casters again, depth only: the
 *       nearest pane joins the opaque depth, producing the with-translucents map
 *       ({@code shadowtex0}) the deferred pass compares receivers against.</li>
 *   <li><b>{@link #glassGBuffer()}</b> &mdash; camera-POV albedo + depth of nearby
 *       panes, so the deferred pass can identify visible glass geometrically.</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
public final class ShadowPipelines {

    /**
     * {@code dst = dst * src}, on colour AND alpha, over a white-cleared buffer.
     *
     * <p>Light crossing N panes keeps {@code prod(transmittance_i)} of each channel, and
     * multiplication is what transmittance physically does &mdash; red glass behind blue
     * glass projects the merged violet, not whichever pane happened to be nearer. It is
     * also order-independent, which is why the colour pass needs no caster sorting.</p>
     *
     * <p>This is safe <em>only</em> because colour and depth carry separate meanings now:
     * WHERE the glass starts is a real depth map ({@link #tintDepth()}), so nothing is
     * decoded from this product. (The old scheme packed a distance into alpha, where a
     * product of distances is garbage &mdash; that is what used to punch white holes in
     * the pool.)</p>
     */
    private static final BlendFunction MULTIPLY = new BlendFunction(
            SourceFactor.ZERO, DestFactor.SRC_COLOR,
            SourceFactor.ZERO, DestFactor.SRC_ALPHA);

    private static GluePipeline depthPipeline;
    private static GluePipeline tintColorPipeline;
    private static GluePipeline tintDepthPipeline;
    private static GluePipeline glassGBufferPipeline;

    private ShadowPipelines() {
    }

    /** Build the pipelines. Call once, from client init &mdash; not mid-frame. */
    public static void init() {
        if (depthPipeline != null) return;

        depthPipeline = base("shadow_depth", "internal/light/shadow_depth")
                .alphaCutout(0.1f)
                .noBlend()
                .colorWrite(false)
                .depthWrite(true)
                .build();

        // Colour and depth follow DIFFERENT rules, hence two pipelines over the same
        // translucent casters. Colour: every pane on the ray multiplies in (that is what
        // transmittance does -- red behind blue merges to violet), no depth write, but
        // still depth-TESTED against the opaque map already in the buffer so a pane
        // hidden behind a wall tints nothing. Depth: nearest pane only, no colour, into
        // the live buffer on top of the opaque depth -- that becomes the
        // with-translucents map the deferred pass compares against.
        tintColorPipeline = base("shadow_tint_color", "internal/light/shadow_tint")
                .noAlphaCutout()
                .blend(MULTIPLY)
                .colorWrite(true)
                .depthWrite(false)
                .depthTest(DepthTestFunction.LESS_DEPTH_TEST)
                .build();

        tintDepthPipeline = base("shadow_tint_depth", "internal/light/shadow_tint")
                .noAlphaCutout()
                .noBlend()
                .colorWrite(false)
                .depthWrite(true)
                .depthTest(DepthTestFunction.LESS_DEPTH_TEST)
                .build();

        // Glass into the shared material G-buffer (see internal/light/glass_gbuffer.fsh). A post-hoc
        // re-render of nearby panes redirected into the material attachments (1,2), tested READ-ONLY
        // (LEQUAL, no depth write) against the borrowed MAIN depth so a pane hidden behind an opaque
        // wall fails and never overwrites the terrain id there. A visible pane's re-rendered z is not
        // bit-identical to the stored one, which alone would dither the equality -- so the RenderType
        // adds a polygon offset (see glassGBuffer()) that biases the pane a hair toward the camera:
        // a visible pane then passes cleanly, an occluded one (a block+ behind) still fails. No blend,
        // no cutout: fully transparent texels still carry the id, so clear glass is identified too.
        glassGBufferPipeline = base("glass_gbuffer", "internal/light/glass_gbuffer")
                .noAlphaCutout()
                .noBlend()
                .colorWrite(true)
                .depthWrite(false)
                .depthTest(DepthTestFunction.LEQUAL_DEPTH_TEST)
                .depthBias(-1.0f, -10.0f)
                .build();
    }

    private static GluePipeline.Builder base(String name, String fragShader) {
        return GluePipeline.builder(
                        ResourceLocation.fromNamespaceAndPath("glue", name),
                        ResourceLocation.fromNamespaceAndPath("glue", "internal/light/shadow"),
                        ResourceLocation.fromNamespaceAndPath("glue", fragShader))
                .snippet(RenderPipelines.MATRICES_FOG_SNIPPET)
                .vertexFormat(DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS)
                .samplers("Sampler0")
                // Backface culling means a ray through a block crosses exactly one of
                // its faces, so a pane tints the light once rather than once per face.
                .cull(true)
                .irisProgram("TERRAIN")
                .category(GluePipeline.PipelineCategory.BLOCK);
    }

    public static RenderType depth() {
        // No mipmap: the alpha cutout wants per-texel alpha, or leaf/grate shadows
        // lose their lace to averaged alpha at distance.
        return blockAtlasType(depthPipeline, "shadow_depth", false);
    }

    public static RenderType tintColor() {
        // MIPPED, and it is load-bearing: internal/light/shadow_tint.fsh samples mip 4, where
        // a standard 16x16 block sprite becomes one average texel. Coarser atlas mips
        // cross sprite boundaries; without mipmaps the pane pattern is projected.
        return blockAtlasType(tintColorPipeline, "shadow_tint_color", true);
    }

    public static RenderType tintDepth() {
        // Mipped for the same reason -- this pass runs the same fragment shader and
        // its discard reads the same averaged alpha, so the two passes agree on
        // which texels exist.
        return blockAtlasType(tintDepthPipeline, "shadow_tint_depth", true);
    }

    public static RenderType glassGBuffer() {
        // Vanilla renders translucent terrain with the MIPPED block sheet; match it, since this
        // buffer's albedo stands in for the pane the player is looking at. The pane is biased toward
        // the camera by the pipeline's depth bias (see glassGBufferPipeline) so its read-only LEQUAL
        // test against the main depth passes cleanly for a visible pane (no z-fight dither) while an
        // occluded pane, a block or more behind, still fails rather than overwriting the terrain id.
        return blockAtlasType(glassGBufferPipeline, "glass_gbuffer", true);
    }

    /**
     * Block atlas, no lightmap: a shadow map cares about geometry, not about how lit it is.
     *
     * <p>{@code sortOnUpload} must be OFF. With it on, {@code endBatch} re-sorts every quad
     * back-to-front from the origin &mdash; and bake geometry is light-relative, so the
     * origin is the light: the farthest pane draws first and every nearer one then passes
     * the LESS depth test and multiply-blends on top. That re-introduces exactly the
     * product-of-panes alpha the front-to-back + depth-test scheme exists to prevent
     * (see {@link #MULTIPLY}). Off, quads draw in submission order, which
     * {@code LightDepthSceneRenderer} arranges front-to-back from the light.</p>
     *
     * <p>The {@code TextureStateShard} boolean is <b>mipmap</b> (verified against
     * {@code BLOCK_SHEET_MIPPED} vs {@code BLOCK_SHEET} in the 1.21.8 bytecode).</p>
     */
    private static RenderType blockAtlasType(GluePipeline pipeline, String key, boolean mipmap) {
        return pipeline.renderType(TextureAtlas.LOCATION_BLOCKS, key + "#mip" + mipmap, false, texture ->
                RenderType.CompositeState.builder()
                        .setTextureState(new RenderStateShard.TextureStateShard(texture, mipmap))
                        .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                        .createCompositeState(false));
    }
}
