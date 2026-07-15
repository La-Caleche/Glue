package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source patch for the vanilla {@code core/entity} and {@code core/particle} shaders: adds two
 * extra fragment outputs -- albedo+packed-normal (attachment 1) and material id (attachment 2)
 * -- so a single entity/particle draw fills the G-buffer alongside the lit colour. The vanilla
 * equivalent of {@code SodiumMaterialShaderPatch}; applied at the {@code ShaderManager}
 * source seam, after {@code #moj_import}s are inlined and before {@code #define}s are injected.
 *
 * <p>These outputs only reach real attachments while the G-buffer FBO is bound with a matching
 * draw-buffer list; against the plain single-attachment main target the writes are discarded,
 * so patched shaders render normally when the capture is inactive.
 *
 * <p>{@code core/*} shaders are {@code #version 150}, which needs
 * {@code GL_ARB_explicit_attrib_location} for {@code layout(location = N)} on fragment outputs.
 */
public final class CoreShaderMaterialPatch {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/gbuffer");

    private static final ResourceLocation ENTITY = ResourceLocation.withDefaultNamespace("core/entity");
    private static final ResourceLocation PARTICLE = ResourceLocation.withDefaultNamespace("core/particle");

    private static final String MARKER = "glue_AlbedoNormal";
    private static final String VERSION_ANCHOR = "#version 150";
    private static final String FRAG_OUT_ANCHOR = "out vec4 fragColor;";

    // Shared GLSL injected into every patched fragment shader.
    private static final String FRAG_COMMON = """


            #extension GL_ARB_explicit_attrib_location : enable
            """;
    private static final String FRAG_DECLS = """

            layout(location = 1) out vec4 glue_AlbedoNormal;
            layout(location = 2) out vec4 glue_MaterialId;

            vec3 glue_srgbToLinear(vec3 c) {
                vec3 lo = c / 12.92;
                vec3 hi = pow((c + 0.055) / 1.055, vec3(2.4));
                return mix(lo, hi, step(vec3(0.04045), c));
            }
            vec2 glue_signNotZero(vec2 v) {
                return vec2(v.x >= 0.0 ? 1.0 : -1.0, v.y >= 0.0 ? 1.0 : -1.0);
            }
            float glue_packNormal(vec3 n) {
                n /= abs(n.x) + abs(n.y) + abs(n.z);
                vec2 oct = n.xy;
                if (n.z < 0.0) oct = (vec2(1.0) - abs(oct.yx)) * glue_signNotZero(oct);
                vec2 e = floor((oct * 0.5 + 0.5) * 15.0 + 0.5);
                return (e.x + e.y * 16.0) / 255.0;
            }
            """;

    private static boolean rejectionLogged;

    private CoreShaderMaterialPatch() {
    }

    public static String patch(ResourceLocation location, ShaderType type, String source) {
        if (source == null || source.contains(MARKER)) return source;
        boolean entity = ENTITY.equals(location);
        boolean particle = PARTICLE.equals(location);
        if (!entity && !particle) return source;

        try {
            if (type == ShaderType.VERTEX) {
                return entity ? patchEntityVertex(source) : patchParticleVertex(source);
            }
            if (type == ShaderType.FRAGMENT) {
                return entity ? patchEntityFragment(source) : patchParticleFragment(source);
            }
        } catch (RuntimeException exception) {
            if (!rejectionLogged) {
                rejectionLogged = true;
                LOGGER.error("[Glue] Could not patch {} {} for the material G-buffer; entities/particles "
                        + "will not be captured", location, type, exception);
            }
        }
        return source;
    }

    // core/entity: pass the raw model colour + a camera-relative-world normal to the fragment
    // stage. ModelViewMat carries the model rotation (the view rotation lives in ProjMat), so
    // its 3x3 takes the model normal to the same space Lumos reconstructs positions in.
    private static String patchEntityVertex(String source) {
        String patched = insertAfter(source, "out vec2 texCoord0;", """


                out vec4 glue_RawColor;
                out vec3 glue_Normal;""");
        patched = insertAfter(patched, "    texCoord0 = UV0;", """


                    glue_RawColor = Color;
                    glue_Normal = mat3(ModelViewMat) * Normal;""");
        return patched;
    }

    private static String patchEntityFragment(String source) {
        String patched = insertAfter(source, VERSION_ANCHOR, FRAG_COMMON);
        patched = insertAfter(patched, FRAG_OUT_ANCHOR, FRAG_DECLS + """

                in vec4 glue_RawColor;
                in vec3 glue_Normal;
                """);
        // `color` still holds the raw texel here (before the vertex-colour/lightmap multiply),
        // which is exactly the albedo we want. Model tint kept as ratios, brightness divided out.
        patched = insertBefore(patched, "    color *= vertexColor * ColorModulator;", """
                    {
                        float glue_shade = max(glue_RawColor.r, max(glue_RawColor.g, glue_RawColor.b));
                        vec3 glue_tint = glue_shade > 1e-4 ? glue_RawColor.rgb / glue_shade : vec3(1.0);
                        glue_AlbedoNormal = vec4(glue_srgbToLinear(color.rgb * glue_tint),
                                                 glue_packNormal(normalize(glue_Normal)));
                        glue_MaterialId = vec4(2.0 / 255.0, 0.0, 0.0, 1.0);
                    }
                """);
        return patched;
    }

    private static String patchParticleVertex(String source) {
        String patched = insertAfter(source, "out vec4 vertexColor;", """


                out vec4 glue_RawColor;""");
        return insertAfter(patched, "    texCoord0 = UV0;", """


                    glue_RawColor = Color;""");
    }

    private static String patchParticleFragment(String source) {
        String patched = insertAfter(source, VERSION_ANCHOR, FRAG_COMMON);
        patched = insertAfter(patched, FRAG_OUT_ANCHOR, FRAG_DECLS + """

                in vec4 glue_RawColor;
                """);
        // Capture the raw texel so albedo excludes the vanilla lightmap folded into vertexColor.
        patched = patched.replace(
                "    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;",
                """
                    vec4 glue_texel = texture(Sampler0, texCoord0);
                    vec4 color = glue_texel * vertexColor * ColorModulator;""");
        // Billboards have no surface normal; store a neutral one, tag as PARTICLE.
        patched = insertBefore(patched, "    fragColor = apply_fog(", """
                    glue_AlbedoNormal = vec4(glue_srgbToLinear(glue_texel.rgb * glue_RawColor.rgb),
                                             glue_packNormal(vec3(0.0, 0.0, 1.0)));
                    glue_MaterialId = vec4(3.0 / 255.0, 0.0, 0.0, 1.0);
                """);
        return patched;
    }

    private static String insertAfter(String source, String anchor, String insertion) {
        int index = source.indexOf(anchor);
        if (index < 0) throw new IllegalStateException("anchor not found: " + anchor);
        index += anchor.length();
        return source.substring(0, index) + insertion + source.substring(index);
    }

    private static String insertBefore(String source, String anchor, String insertion) {
        int index = source.indexOf(anchor);
        if (index < 0) throw new IllegalStateException("anchor not found: " + anchor);
        return source.substring(0, index) + insertion + source.substring(index);
    }
}
