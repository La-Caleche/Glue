package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreShaderMaterialPatchTest {

    private static final ResourceLocation ENTITY =
            ResourceLocation.withDefaultNamespace("core/entity");

    @Test
    void entityPatchWritesMaterialAndOwningDepth() {
        String vertex = """
                #version 150
                out vec2 texCoord0;
                void main() {
                    texCoord0 = UV0;
                }
                """;
        String fragment = """
                #version 150
                out vec4 fragColor;
                void main() {
                    vec4 color = vec4(1.0);
                    color *= vertexColor * ColorModulator;
                }
                """;

        String patchedVertex = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.VERTEX, vertex);
        String patchedFragment = CoreShaderMaterialPatch.patch(ENTITY, ShaderType.FRAGMENT, fragment);

        assertTrue(patchedVertex.contains("out vec4 glue_RawColor;"));
        assertTrue(patchedFragment.contains("layout(location = 2) out vec4 glue_MaterialId;"));
        assertTrue(patchedFragment.contains("glue_packDepth24(gl_FragCoord.z)"));
        assertTrue(CoreShaderMaterialPatch.isEntityReady());
    }
}
