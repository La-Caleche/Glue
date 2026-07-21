package fr.lacaleche.glue.client.render.internal.gbuffer;

import com.mojang.blaze3d.shaders.ShaderType;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Source patch for the vanilla {@code core/terrain}, {@code core/entity} and {@code core/particle}
 * shaders: adds three extra fragment outputs -- albedo+packed-normal (attachment 1), material id
 * (attachment 2) and material properties (attachment 3) -- so a single vanilla draw fills the
 * G-buffer alongside the lit colour. The vanilla equivalent of {@code SodiumMaterialShaderPatch};
 * applied at the {@code ShaderManager} source seam, after {@code #moj_import}s are inlined and
 * before {@code #define}s are injected.
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

    private static final String VERTEX_MARKER = "out vec4 glue_RawColor;";
    private static final String FRAGMENT_MARKER = "layout(location = 1) out vec4 glue_AlbedoNormal;";
    private static final String VERSION_ANCHOR = "#version 150";
    private static final String FRAG_OUT_ANCHOR = "out vec4 fragColor;";

    // Shared GLSL injected into every patched fragment shader.
    private static final String FRAG_COMMON = """


            #extension GL_ARB_explicit_attrib_location : enable
            """;
    private static final String FRAG_DECLS = """

            layout(location = 1) out vec4 glue_AlbedoNormal;
            layout(location = 2) out vec4 glue_MaterialId;
            layout(location = 3) out vec4 glue_MaterialProps;

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
            vec3 glue_packDepth24(float depth) {
                float value = floor(clamp(depth, 0.0, 1.0) * 16777215.0 + 0.5);
                float high = floor(value / 65536.0);
                value -= high * 65536.0;
                float middle = floor(value / 256.0);
                float low = value - middle * 256.0;
                return vec3(high, middle, low) / 255.0;
            }
            """;

    /**
     * The vanilla core shaders Lumos captures, each owning its own per-stage patch state.
     *
     * <p>A target is READY only once both of its stages carry our outputs. Consumers gate their
     * FBO redirect on that, so a future Minecraft whose shader source has drifted away from an
     * anchor degrades to "this surface class is not captured" rather than to a half-patched
     * shader or a silently id-less surface.
     */
    private enum Target {
        TERRAIN(ResourceLocation.withDefaultNamespace("core/terrain")),
        ENTITY(ResourceLocation.withDefaultNamespace("core/entity")),
        PARTICLE(ResourceLocation.withDefaultNamespace("core/particle"));

        private final ResourceLocation location;
        private boolean vertexPatched;
        private boolean fragmentPatched;

        Target(ResourceLocation location) {
            this.location = location;
        }

        static Target of(ResourceLocation location) {
            for (Target target : values()) {
                if (target.location.equals(location)) return target;
            }
            return null;
        }

        boolean isReady() {
            return vertexPatched && fragmentPatched;
        }

        void mark(ShaderType type, boolean patched) {
            if (type == ShaderType.VERTEX) vertexPatched = patched;
            else if (type == ShaderType.FRAGMENT) fragmentPatched = patched;
        }
    }

    private static boolean rejectionLogged;

    private CoreShaderMaterialPatch() {
    }

    /** True once both stages of {@code core/terrain} carry our G-buffer outputs. */
    public static boolean isTerrainReady() {
        return Target.TERRAIN.isReady();
    }

    /** True once both stages of {@code core/entity} carry our G-buffer outputs. */
    public static boolean isEntityReady() {
        return Target.ENTITY.isReady();
    }

    /** True once both stages of {@code core/particle} carry our G-buffer outputs. */
    public static boolean isParticleReady() {
        return Target.PARTICLE.isReady();
    }

    public static String patch(ResourceLocation location, ShaderType type, String source) {
        Target target = Target.of(location);
        if (target == null) return source;
        if (source == null) {
            target.mark(type, false);
            return null;
        }

        try {
            if (type == ShaderType.VERTEX) {
                if (source.contains(VERTEX_MARKER)) {
                    target.mark(type, true);
                    return source;
                }
                target.mark(type, false);
                String patched = switch (target) {
                    case TERRAIN, ENTITY -> patchColorNormalVertex(source);
                    case PARTICLE -> patchParticleVertex(source);
                };
                target.mark(type, true);
                return patched;
            }
            if (type == ShaderType.FRAGMENT) {
                if (source.contains(FRAGMENT_MARKER)) {
                    target.mark(type, true);
                    return source;
                }
                target.mark(type, false);
                String patched = switch (target) {
                    case TERRAIN -> patchTerrainFragment(source);
                    case ENTITY -> patchEntityFragment(source);
                    case PARTICLE -> patchParticleFragment(source);
                };
                target.mark(type, true);
                return patched;
            }
        } catch (RuntimeException exception) {
            target.mark(type, false);
            if (!rejectionLogged) {
                rejectionLogged = true;
                LOGGER.error("[Glue] Could not patch {} {} for the material G-buffer; that surface "
                        + "class will not be captured", location, type, exception);
            }
        }
        return source;
    }

    // core/terrain and core/entity: pass the raw model colour + a camera-relative-world normal to
    // the fragment stage. In BOTH, the Normal attribute is ALREADY in camera-relative world space
    // (the space Lumos reconstructs positions in): Minecraft bakes the entity/part pose -- including
    // its normal matrix -- into it CPU-side, and chunk meshes are baked in world space with
    // world-space face normals. ModelViewMat carries only the camera view rotation, so multiplying
    // by it here would wrongly rotate the normal into view space and make shading swim with the
    // camera. Pass Normal straight through.
    private static String patchColorNormalVertex(String source) {
        String patched = insertAfter(source, "out vec2 texCoord0;", """


                out vec4 glue_RawColor;
                out vec3 glue_Normal;""");
        patched = insertAfter(patched, "    texCoord0 = UV0;", """


                    glue_RawColor = Color;
                    glue_Normal = Normal;""");
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
        //
        // Only a texel that survives to cover the pixel may claim it. The entity types drawn by this
        // shader are captured because they are really alpha-cutout, depth-writing geometry, but the
        // same shader still draws the see-through cases their blend exists for: a part-transparent
        // skin layer, or an entity faded out whole via ColorModulator. Gate on the alpha that
        // actually reaches the blend equation -- texel * model tint * entity fade (Color.a arrives
        // here as vertexColor.a; minecraft_mix_light shades only rgb). Below half, the surface behind
        // still supplies most of the pixel and must keep ownership.
        //
        // The else-branch is not optional: an output left unwritten on a path is UNDEFINED in GLSL,
        // not "left alone", so it could stamp garbage. Zero is the cleared, unclaimed value -- the
        // same state a pixel no draw ever captured has, which the deferred pass already treats as
        // "no stored albedo" and resolves through its estimate path.
        patched = insertBefore(patched, "    color *= vertexColor * ColorModulator;", """
                    {
                        float glue_alpha = color.a * vertexColor.a * ColorModulator.a;
                        if (glue_alpha >= 0.5) {
                            float glue_shade = max(glue_RawColor.r, max(glue_RawColor.g, glue_RawColor.b));
                            vec3 glue_tint = glue_shade > 1e-4 ? glue_RawColor.rgb / glue_shade : vec3(1.0);
                            glue_AlbedoNormal = vec4(glue_srgbToLinear(color.rgb * glue_tint),
                                                     glue_packNormal(normalize(glue_Normal)));
                            glue_MaterialId = vec4(2.0 / 255.0, glue_packDepth24(gl_FragCoord.z));
                            glue_MaterialProps = vec4(0.7, 0.0, 0.04, 1.0);
                        } else {
                            glue_AlbedoNormal = vec4(0.0);
                            glue_MaterialId = vec4(0.0);
                            glue_MaterialProps = vec4(0.0);
                        }
                    }
                """);
        return patched;
    }

    private static String patchTerrainFragment(String source) {
        String patched = insertAfter(source, VERSION_ANCHOR, FRAG_COMMON);
        patched = insertAfter(patched, FRAG_OUT_ANCHOR, FRAG_DECLS + """

                in vec4 glue_RawColor;
                in vec3 glue_Normal;
                """);
        // core/terrain folds the lightmap into vertexColor in the VERTEX stage, so the raw texel is
        // the only albedo signal left in this stage -- capture it before the multiply consumes it.
        patched = replaceOnce(patched,
                "    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;",
                """
                    vec4 glue_texel = texture(Sampler0, texCoord0);
                    vec4 color = glue_texel * vertexColor * ColorModulator;""");
        // Written just before the fog line, which puts it AFTER the ALPHA_CUTOUT discard: a cut-out
        // fragment is not drawn, so it must not claim this pixel's material either. glue_RawColor is
        // the pre-lightmap Color, into whose RGB vanilla bakes a scalar AO/face-shade coefficient --
        // the same shade-divide the entity patch uses keeps the biome/model tint as channel ratios
        // and drops that brightness, leaving reflectance.
        patched = insertBefore(patched, "    fragColor = apply_fog(", """
                    {
                        float glue_shade = max(glue_RawColor.r, max(glue_RawColor.g, glue_RawColor.b));
                        vec3 glue_tint = glue_shade > 1e-4 ? glue_RawColor.rgb / glue_shade : vec3(1.0);
                        glue_AlbedoNormal = vec4(glue_srgbToLinear(glue_texel.rgb * glue_tint),
                                                 glue_packNormal(normalize(glue_Normal)));
                        glue_MaterialId = vec4(1.0 / 255.0, glue_packDepth24(gl_FragCoord.z));
                        // Generic terrain: rough dielectric. Specific materials (water, metal) get
                        // their real properties from a dedicated capture that overwrites this pixel.
                        glue_MaterialProps = vec4(1.0, 0.0, 0.04, 1.0);
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
        patched = replaceOnce(patched,
                "    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;",
                """
                    vec4 glue_texel = texture(Sampler0, texCoord0);
                    vec4 color = glue_texel * vertexColor * ColorModulator;""");
        // Billboards have no surface normal; store a neutral one, tag as PARTICLE.
        patched = insertBefore(patched, "    fragColor = apply_fog(", """
                    glue_AlbedoNormal = vec4(glue_srgbToLinear(glue_texel.rgb * glue_RawColor.rgb),
                                             glue_packNormal(vec3(0.0, 0.0, 1.0)));
                    glue_MaterialId = vec4(3.0 / 255.0, glue_packDepth24(gl_FragCoord.z));
                    glue_MaterialProps = vec4(1.0, 0.0, 0.04, 1.0);
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

    /** {@code String.replace} on a drifted anchor silently returns the source unchanged, which would
     *  leave a half-patched shader that still reports patched -- and a terrain shader that claims a
     *  material id it never writes takes the whole world with it. Fail loudly like the inserts do. */
    private static String replaceOnce(String source, String anchor, String replacement) {
        if (!source.contains(anchor)) throw new IllegalStateException("anchor not found: " + anchor);
        return source.replace(anchor, replacement);
    }
}
