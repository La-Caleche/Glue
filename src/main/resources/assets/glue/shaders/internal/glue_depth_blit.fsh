#version 150

uniform sampler2D CaptureColor;
uniform sampler2D CaptureDepth;
uniform sampler2D SceneDepth;
uniform int HasSceneDepth;
uniform int IsAdditive;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec4 color = texture(CaptureColor, texCoord);

    if (IsAdditive == 1) {
        // Additive: discard near-black pixels (they add nothing).
        float luminance = dot(color.rgb, vec3(0.299, 0.587, 0.114));
        if (luminance < 0.004) discard;

        // Depth test against Iris scene depth for wall occlusion.
        if (HasSceneDepth == 1) {
            float capturedZ = texture(CaptureDepth, texCoord).r;
            float sceneZ = texture(SceneDepth, texCoord).r;
            if (capturedZ >= sceneZ) discard;
        }
    } else {
        // Alpha: standard alpha discard.
        if (color.a < 0.005) discard;

        // Depth test against Iris scene depth.
        if (HasSceneDepth == 1) {
            float capturedZ = texture(CaptureDepth, texCoord).r;
            float sceneZ = texture(SceneDepth, texCoord).r;
            if (capturedZ >= sceneZ) discard;
        }
    }

    fragColor = color;
}
