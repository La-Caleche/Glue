package fr.lacaleche.glue.client.render.internal.material;

import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Strict source patch for Sodium 0.7.3's opaque chunk shaders. */
public final class SodiumMaterialShaderPatch {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/material-buffer");
    private static final ResourceLocation VERTEX_SHADER = ResourceLocation.fromNamespaceAndPath(
            "sodium", "blocks/block_layer_opaque.vsh");
    private static final ResourceLocation FRAGMENT_SHADER = ResourceLocation.fromNamespaceAndPath(
            "sodium", "blocks/block_layer_opaque.fsh");

    private static final String VERTEX_OUTPUT_ANCHOR = "out vec2 v_TexCoord;";
    private static final String VERTEX_POSITION_ANCHOR =
            "vec3 position = _vert_position + translation;";
    private static final String FRAGMENT_TEXCOORD_ANCHOR = "in vec2 v_TexCoord;";
    private static final String FRAGMENT_OUTPUT_ANCHOR = "out vec4 fragColor;";
    private static final String FRAGMENT_SAMPLE_ANCHOR =
            "vec4 color = texture(u_BlockTex, v_TexCoord, lodBias);";
    private static final String FRAGMENT_COLOR_ANCHOR =
            "fragColor = _linearFog(color, v_FragDistance, u_FogColor, u_EnvironmentFog, u_RenderFog);";
    private static boolean vertexPatched;
    private static boolean fragmentPatched;
    private static boolean vertexRejected;
    private static boolean fragmentRejected;
    private static boolean rejectionLogged;

    private SodiumMaterialShaderPatch() {
    }

    /**
     * Each shader carries its own patch state, refreshed on every request. Sodium gives no
     * reload-cycle boundary and no ordering guarantee between the two shaders, so the state
     * must never depend on one of them being requested before the other: a shader that is not
     * re-requested has not changed, and its previous result therefore still holds.
     */
    public static String patch(ResourceLocation location, String source) {
        if (VERTEX_SHADER.equals(location)) return patchVertex(source);
        if (FRAGMENT_SHADER.equals(location)) return patchFragment(source);
        return source;
    }

    private static String patchVertex(String source) {
        if (source.contains("out vec4 glue_RawColor;")) {
            acceptVertex();
            return source;
        }
        if (!containsAll(source, VERTEX_OUTPUT_ANCHOR, VERTEX_POSITION_ANCHOR)) {
            vertexPatched = false;
            vertexRejected = true;
            logRejection(VERTEX_SHADER);
            return source;
        }

        String patched = insertAfter(source, VERTEX_OUTPUT_ANCHOR, """

                out vec4 glue_RawColor;
                out vec3 glue_WorldPos;""");
        patched = insertAfter(patched, VERTEX_POSITION_ANCHOR, """

                    glue_RawColor = _vert_color;
                    glue_WorldPos = position;""");
        acceptVertex();
        return patched;
    }

    private static String patchFragment(String source) {
        if (source.contains("layout(location = 1) out vec4 glue_Material;")) {
            acceptFragment();
            return source;
        }
        if (!containsAll(source, FRAGMENT_TEXCOORD_ANCHOR, FRAGMENT_OUTPUT_ANCHOR,
                FRAGMENT_SAMPLE_ANCHOR, FRAGMENT_COLOR_ANCHOR)) {
            fragmentPatched = false;
            fragmentRejected = true;
            logRejection(FRAGMENT_SHADER);
            return source;
        }

        String patched = insertAfter(source, FRAGMENT_TEXCOORD_ANCHOR, """

                in vec4 glue_RawColor;
                in vec3 glue_WorldPos;""");
        patched = insertAfter(patched, FRAGMENT_OUTPUT_ANCHOR, """

                layout(location = 1) out vec4 glue_Material;

                vec3 glueSrgbToLinear(vec3 color) {
                    vec3 low = color / 12.92;
                    vec3 high = pow((color + 0.055) / 1.055, vec3(2.4));
                    return mix(low, high, step(vec3(0.04045), color));
                }

                vec2 glueSignNotZero(vec2 value) {
                    return vec2(value.x >= 0.0 ? 1.0 : -1.0,
                                value.y >= 0.0 ? 1.0 : -1.0);
                }

                float gluePackNormal(vec3 normal) {
                    normal /= abs(normal.x) + abs(normal.y) + abs(normal.z);
                    vec2 octahedral = normal.xy;
                    if (normal.z < 0.0) {
                        octahedral = (vec2(1.0) - abs(octahedral.yx))
                                   * glueSignNotZero(octahedral);
                    }
                    vec2 encoded = floor((octahedral * 0.5 + 0.5) * 15.0 + 0.5);
                    return (encoded.x + encoded.y * 16.0) / 255.0;
                }""");
        patched = patched.replace(FRAGMENT_SAMPLE_ANCHOR, """
                vec4 glue_Texel = texture(u_BlockTex, v_TexCoord, lodBias);
                    vec4 color = glue_Texel;""");
        patched = insertBefore(patched, FRAGMENT_COLOR_ANCHOR, """
                    float glueShade = max(glue_RawColor.r,
                                          max(glue_RawColor.g, glue_RawColor.b));
                    vec3 glueTint = glueShade > 1e-4
                            ? glue_RawColor.rgb / glueShade
                            : vec3(1.0);
                    vec3 glueNormal = normalize(cross(dFdx(glue_WorldPos),
                                                      dFdy(glue_WorldPos)));
                    // Screen-space derivative normals jitter at grazing angles: perspective
                    // makes the finite difference a poor tangent estimate, and it varies per
                    // 2x2 quad, which reads as a woven grain the vanilla vertex normal never
                    // shows. Terrain is overwhelmingly axis-aligned, so snap a near-axis normal
                    // to its exact axis; a genuinely diagonal face keeps the derived value.
                    vec3 glueAxis = abs(glueNormal);
                    float glueMaxAxis = max(glueAxis.x, max(glueAxis.y, glueAxis.z));
                    if (glueMaxAxis > 0.9) {
                        glueNormal = (glueAxis.x >= glueAxis.y && glueAxis.x >= glueAxis.z)
                                ? vec3(sign(glueNormal.x), 0.0, 0.0)
                                : ((glueAxis.y >= glueAxis.z)
                                        ? vec3(0.0, sign(glueNormal.y), 0.0)
                                        : vec3(0.0, 0.0, sign(glueNormal.z)));
                    }
                    glue_Material = vec4(
                            glueSrgbToLinear(glue_Texel.rgb * glueTint),
                            gluePackNormal(glueNormal));

                """);
        acceptFragment();
        return patched;
    }

    static boolean isReady() {
        return !isRejected() && vertexPatched && fragmentPatched;
    }

    static boolean isRejected() {
        return vertexRejected || fragmentRejected;
    }

    private static void acceptVertex() {
        vertexPatched = true;
        vertexRejected = false;
    }

    private static void acceptFragment() {
        fragmentPatched = true;
        fragmentRejected = false;
    }

    private static void logRejection(ResourceLocation shader) {
        if (!rejectionLogged) {
            rejectionLogged = true;
            LOGGER.error("Sodium material adapter disabled: shader {} does not match Sodium 0.7.3", shader);
        }
    }

    private static boolean containsAll(String source, String... anchors) {
        for (String anchor : anchors) {
            if (!source.contains(anchor)) return false;
        }
        return true;
    }

    private static String insertAfter(String source, String anchor, String insertion) {
        int index = source.indexOf(anchor) + anchor.length();
        return source.substring(0, index) + insertion + source.substring(index);
    }

    private static String insertBefore(String source, String anchor, String insertion) {
        int index = source.indexOf(anchor);
        return source.substring(0, index) + insertion + source.substring(index);
    }
}
