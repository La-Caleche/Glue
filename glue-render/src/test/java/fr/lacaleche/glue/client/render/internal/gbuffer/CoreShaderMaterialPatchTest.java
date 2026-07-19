package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreShaderMaterialPatchTest {

    private static final ResourceLocation ENTITY =
            ResourceLocation.withDefaultNamespace("core/entity");
    private static final ResourceLocation PARTICLE =
            ResourceLocation.withDefaultNamespace("core/particle");
    private static final ResourceLocation TERRAIN =
            ResourceLocation.withDefaultNamespace("core/terrain");

    private static final String ENTITY_VERTEX = """
            #version 150
            out vec2 texCoord0;
            void main() {
                texCoord0 = UV0;
            }
            """;
    /** Mirrors 1.21.8's core/terrain.vsh: the lightmap is folded into vertexColor here, so the
     *  fragment stage can only recover albedo from the raw Color this patch forwards. */
    private static final String TERRAIN_VERTEX = """
            #version 150
            out vec4 vertexColor;
            out vec2 texCoord0;
            void main() {
                vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
                texCoord0 = UV0;
            }
            """;
    /** Mirrors 1.21.8's core/terrain.fsh, including the ALPHA_CUTOUT discard between the texture
     *  sample and the fog line. */
    private static final String TERRAIN_FRAGMENT = """
            #version 150
            out vec4 fragColor;
            void main() {
                vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
            #ifdef ALPHA_CUTOUT
                if (color.a < ALPHA_CUTOUT) {
                    discard;
                }
            #endif
                fragColor = apply_fog(color, sphericalVertexDistance, FogColor);
            }
            """;
    private static final String ENTITY_FRAGMENT = """
            #version 150
            out vec4 fragColor;
            void main() {
                vec4 color = vec4(1.0);
                color *= vertexColor * ColorModulator;
            }
            """;
    private static final String PARTICLE_FRAGMENT = """
            #version 150
            out vec4 fragColor;
            void main() {
                vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
                fragColor = apply_fog(color);
            }
            """;

    @Test
    void entityPatchWritesMaterialAndOwningDepth() {
        String patchedVertex = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.VERTEX, ENTITY_VERTEX);
        String patchedFragment = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.FRAGMENT, ENTITY_FRAGMENT);

        assertTrue(patchedVertex.contains("out vec4 glue_RawColor;"));
        assertTrue(patchedFragment.contains("layout(location = 2) out vec4 glue_MaterialId;"));
        assertTrue(patchedFragment.contains("glue_packDepth24(gl_FragCoord.z)"));
        assertTrue(CoreShaderMaterialPatch.isEntityReady());
    }

    /** The entity Normal attribute is already camera-relative world space; it must NOT be
     *  re-rotated by ModelViewMat (the camera view rotation), or entity shading swims. */
    @Test
    void entityVertexKeepsNormalInWorldSpace() {
        String patchedVertex = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.VERTEX, ENTITY_VERTEX);

        assertTrue(patchedVertex.contains("glue_Normal = Normal;"));
        assertFalse(patchedVertex.contains("mat3(ModelViewMat) * Normal"));
    }

    @Test
    void particlePatchWritesParticleIdAndDepth() {
        String patchedFragment = CoreShaderMaterialPatch.patch(PARTICLE, ShaderType.FRAGMENT, PARTICLE_FRAGMENT);

        assertTrue(patchedFragment.contains("glue_MaterialId = vec4(3.0 / 255.0"));
        assertTrue(patchedFragment.contains("glue_packDepth24(gl_FragCoord.z)"));
    }

    @Test
    void particleReadyWhenBothStagesPatched() {
        String vertex = """
                #version 150
                out vec4 vertexColor;
                out vec2 texCoord0;
                void main() {
                    texCoord0 = UV0;
                }
                """;

        CoreShaderMaterialPatch.patch(PARTICLE, ShaderType.VERTEX, vertex);
        CoreShaderMaterialPatch.patch(PARTICLE, ShaderType.FRAGMENT, PARTICLE_FRAGMENT);

        assertTrue(CoreShaderMaterialPatch.isParticleReady());
    }

    @Test
    void terrainPatchWritesTerrainIdAndOwningDepth() {
        String patchedFragment = CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.FRAGMENT, TERRAIN_FRAGMENT);

        assertTrue(patchedFragment.contains("glue_MaterialId = vec4(1.0 / 255.0"));
        assertTrue(patchedFragment.contains("glue_packDepth24(gl_FragCoord.z)"));
        // Alpha 1.0 is the "props present" flag; roughness 1 / metalness 0 / F0 0.04 match the
        // Sodium terrain patch, so both terrain paths hand consumers the same properties.
        assertTrue(patchedFragment.contains("glue_MaterialProps = vec4(1.0, 0.0, 0.04, 1.0);"));
    }

    /** The albedo must come from the RAW texel and the PRE-lightmap Color: core/terrain folds the
     *  lightmap into vertexColor in the vertex stage, so multiplying it in would bake vanilla's
     *  illumination into what is meant to be reflectance. */
    @Test
    void terrainAlbedoExcludesTheLightmap() {
        String patchedVertex = CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.VERTEX, TERRAIN_VERTEX);
        String patchedFragment = CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.FRAGMENT, TERRAIN_FRAGMENT);

        assertTrue(patchedVertex.contains("glue_RawColor = Color;"));
        assertTrue(patchedFragment.contains("vec4 glue_texel = texture(Sampler0, texCoord0);"));
        assertTrue(patchedFragment.contains(
                "glue_AlbedoNormal = vec4(glue_srgbToLinear(glue_texel.rgb * glue_tint),"));
    }

    /** A cut-out fragment is discarded and never drawn, so it must not claim the pixel's material
     *  either -- the G-buffer write has to sit after the discard and before the fog line. */
    @Test
    void terrainWritesGBufferAfterTheAlphaCutoutDiscard() {
        String patchedFragment = CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.FRAGMENT, TERRAIN_FRAGMENT);

        int discard = patchedFragment.indexOf("discard;");
        int write = patchedFragment.indexOf("glue_MaterialId =");
        int fog = patchedFragment.indexOf("fragColor = apply_fog(");
        assertTrue(discard >= 0 && write > discard, "G-buffer write must follow the cutout discard");
        assertTrue(write < fog, "G-buffer write must precede the fog line");
    }

    /** Terrain shares the entity vertex patch; its Normal is likewise already camera-relative
     *  world space (chunk meshes are baked in world space) and must not be re-rotated. */
    @Test
    void terrainVertexKeepsNormalInWorldSpace() {
        String patchedVertex = CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.VERTEX, TERRAIN_VERTEX);

        assertTrue(patchedVertex.contains("glue_Normal = Normal;"));
        assertFalse(patchedVertex.contains("mat3(ModelViewMat) * Normal"));
    }

    @Test
    void terrainReadyWhenBothStagesPatched() {
        CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.VERTEX, TERRAIN_VERTEX);
        CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.FRAGMENT, TERRAIN_FRAGMENT);

        assertTrue(CoreShaderMaterialPatch.isTerrainReady());
    }

    /**
     * The safety gate behind {@code TerrainMaterialBuffer}: if a future Minecraft moves an anchor,
     * terrain must report NOT ready. Consumers cap light on every pixel they cannot identify, so
     * claiming capability with an unpatched terrain shader would leave the whole world unlit.
     */
    @Test
    void terrainNotReadyWhenFragmentAnchorIsMissing() {
        CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.VERTEX, TERRAIN_VERTEX);
        String noAnchor = """
                #version 150
                void main() {
                }
                """;

        assertEquals(noAnchor, CoreShaderMaterialPatch.patch(TERRAIN, ShaderType.FRAGMENT, noAnchor));
        assertFalse(CoreShaderMaterialPatch.isTerrainReady());
    }

    @Test
    void patchingAnAlreadyPatchedSourceIsIdempotent() {
        String once = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.FRAGMENT, ENTITY_FRAGMENT);
        String twice = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.FRAGMENT, once);

        assertEquals(once, twice);
    }

    @Test
    void nonCoreShaderIsLeftUnchanged() {
        ResourceLocation other = ResourceLocation.withDefaultNamespace("core/blit_screen");
        String source = ENTITY_FRAGMENT;

        assertEquals(source, CoreShaderMaterialPatch.patch(other, ShaderType.FRAGMENT, source));
    }

    /** A shader missing an expected anchor must not throw; the original source is returned so
     *  the entity still renders (just uncaptured). */
    @Test
    void fragmentMissingAnchorReturnsOriginalWithoutThrowing() {
        String noAnchor = """
                #version 150
                void main() {
                }
                """;

        assertEquals(noAnchor, CoreShaderMaterialPatch.patch(ENTITY, ShaderType.FRAGMENT, noAnchor));
    }
}
