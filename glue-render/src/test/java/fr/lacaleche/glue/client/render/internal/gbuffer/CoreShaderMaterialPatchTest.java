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

    private static final String ENTITY_VERTEX = """
            #version 150
            out vec2 texCoord0;
            void main() {
                texCoord0 = UV0;
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
