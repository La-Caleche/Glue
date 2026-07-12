#version 150

// Tonemapping composite of the HDR lighting buffer onto the scene.
// Additive blend (GL_ONE, GL_ONE) is configured by the renderer; alpha is left
// at 0 so the scene's alpha is untouched. Unlike glue_depth_blit.fsh this pass
// does NOT depth-compare -- occlusion was resolved during accumulation.

uniform sampler2D LightTex;     // unit 0: accumulated HDR light
uniform sampler2D SceneTex;     // unit 1: copy of the scene colour, taken pre-composite
uniform float Exposure;
uniform float AlbedoFloor;      // how much light a pitch-black surface still returns

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec3 hdr = texture(LightTex, texCoord).rgb * Exposure;
    if (hdr == vec3(0.0)) discard;

    // Light has to be tinted by what it lands on, or every lit surface washes out
    // to white. The vanilla path has no albedo G-buffer, so the already-shaded
    // scene colour stands in for albedo -- SceneTex is a copy because a pass may
    // not sample the target it renders into. The floor keeps near-black surfaces
    // (unlit at night, which is most of what these lights are for) from
    // swallowing the light entirely.
    vec3 scene = texture(SceneTex, texCoord).rgb;
    vec3 albedo = clamp(scene + AlbedoFloor, 0.0, 1.0);

    vec3 lit = hdr * albedo;
    // Reinhard rolloff: bright/overlapping lights compress toward 1.0 with a
    // smooth knee instead of clipping to flat white.
    vec3 mapped = lit / (lit + vec3(1.0));
    fragColor = vec4(mapped, 0.0);
}
