package fr.lacaleche.glue.client.render.light.internal.gl;

import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightType;
import fr.lacaleche.glue.client.render.light.internal.shadow.ShadowParams;
import fr.lacaleche.glue.client.render.internal.gl.SavedGlState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/** Raw GL pass that adds one light, or one point-light face, to the HDR buffer. */
public final class GlDeferredLightPass {

    private static final Matrix4f IDENTITY = new Matrix4f();
    private final GlLightResources resources;

    public GlDeferredLightPass(GlLightResources resources) {
        this.resources = resources;
    }

    public void render(int lightFramebuffer, int sceneDepth, Matrix4f viewProjection,
                       Matrix4f inverseViewProjection, Vector3d camera, Light light,
                       int width, int height, int[] bounds, @Nullable ShadowParams shadow,
                       int gbufferAlbedo, int gbufferId, int gbufferProps,
                       float[] blobData, int blobCount, float time) {
        int program = resources.program("glue_light_deferred",
                "light/deferred.vsh", "light/deferred.fsh");
        if (program == 0 || lightFramebuffer <= 0 || sceneDepth <= 0) return;

        SavedGlState state = SavedGlState.save();
        try {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lightFramebuffer);
            GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
            GL11.glViewport(0, 0, width, height);
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(bounds[0], bounds[1], bounds[2], bounds[3]);
            GL20.glUseProgram(program);
            setupState();

            bindTexture(program, "SceneDepth", 0, sceneDepth);
            boolean hasGobo = light.type == LightType.GOBO && light.goboTextureId > 0;
            bindTexture(program, "Gobo", 1, hasGobo ? light.goboTextureId : 0);
            resources.uniform1i(program, "HasGobo", hasGobo ? 1 : 0);

            resources.uniformMatrix(program, "InvViewProj", inverseViewProjection);
            resources.uniformMatrix(program, "ViewProj", viewProjection);
            resources.uniform2f(program, "TexelSize", 1f / width, 1f / height);
            resources.uniform1f(program, "Time", time);
            resources.uniform3f(program, "CameraPos",
                    (float) camera.x, (float) camera.y, (float) camera.z);
            resources.uniform3f(program, "LightPos",
                    (float) (light.x - camera.x),
                    (float) (light.y - camera.y),
                    (float) (light.z - camera.z));
            resources.uniform3f(program, "LightColor",
                    light.r * light.intensity, light.g * light.intensity, light.b * light.intensity);
            resources.uniform1f(program, "Range", light.range);
            resources.uniform1i(program, "LightType", light.type.ordinal());
            resources.uniform3f(program, "SpotDir",
                    light.directionX, light.directionY, light.directionZ);
            resources.uniform1f(program, "CosInner", light.cosInner);
            resources.uniform1f(program, "CosOuter", light.cosOuter);
            resources.uniformMatrix(program, "LightMatrix",
                    hasGobo ? goboMatrix(light, camera) : IDENTITY);

            boolean hasShadow = shadow != null && shadow.textureId() > 0;
            bindTexture(program, "ShadowMap", 2, hasShadow ? shadow.textureId() : 0);
            resources.uniform1i(program, "HasShadowMap", hasShadow ? 1 : 0);
            resources.uniformMatrix(program, "LightViewProj",
                    hasShadow ? shadow.lightViewProj() : IDENTITY);
            resources.uniform1f(program, "ShadowTexel",
                    hasShadow && shadow.mapSize() > 0 ? 1f / shadow.mapSize() : 0f);
            resources.uniform1f(program, "ShadowNear", hasShadow ? shadow.near() : 0.05f);
            resources.uniform1f(program, "ShadowFar", hasShadow ? shadow.far() : 1f);
            resources.uniform1f(program, "ShadowFocalY", hasShadow ? shadow.focalY() : 1f);
            resources.uniform1f(program, "LightSize", hasShadow ? shadow.lightSize() : 0.1f);
            resources.uniform1i(program, "ShadowFace", hasShadow ? shadow.faceAxis() : -1);

            boolean hasTint = hasShadow && shadow.tintTextureId() > 0 && shadow.tintDepthId() > 0;
            bindTexture(program, "ShadowTint", 3, hasTint ? shadow.tintTextureId() : 0);
            bindTexture(program, "TintDepth", 6, hasTint ? shadow.tintDepthId() : 0);
            resources.uniform1i(program, "HasShadowTint", hasTint ? 1 : 0);

            boolean hasEntityShadow = hasShadow && shadow.entityDepthId() > 0;
            bindTexture(program, "EntityShadowMap", 11, hasEntityShadow ? shadow.entityDepthId() : 0);
            resources.uniform1i(program, "HasEntityShadow", hasEntityShadow ? 1 : 0);

            boolean hasGBuffer = gbufferAlbedo > 0 && gbufferId > 0;
            bindTexture(program, "GBufferAlbedo", 9, hasGBuffer ? gbufferAlbedo : 0);
            bindTexture(program, "GBufferId", 10, hasGBuffer ? gbufferId : 0);
            resources.uniform1i(program, "HasGBuffer", hasGBuffer ? 1 : 0);

            bindTexture(program, "MaterialProps", 12, gbufferProps > 0 ? gbufferProps : 0);
            resources.uniform1i(program, "HasMaterialProps", gbufferProps > 0 ? 1 : 0);

            // Entity blob shadows: two vec4 per capsule (lower axis + radius, upper axis).
            if (blobCount > 0) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    FloatBuffer buffer = stack.mallocFloat(blobCount * 8);
                    buffer.put(blobData, 0, blobCount * 8).flip();
                    resources.uniform4fv(program, "ShadowBlobs", buffer);
                }
            }
            resources.uniform1i(program, "ShadowBlobCount", blobCount);
            resources.drawFullscreen(program);
        } finally {
            state.restore();
        }
    }

    private static void bindTexture(int program, String uniform, int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        int location = GL20.glGetUniformLocation(program, uniform);
        if (location >= 0) GL20.glUniform1i(location, unit);
    }

    private static Matrix4f goboMatrix(Light light, Vector3d camera) {
        float halfAngle = (float) Math.acos(Math.clamp(light.cosOuter, -1f, 1f));
        float fieldOfView = Math.clamp(halfAngle * 2f, 0.1f, (float) Math.PI - 0.1f);
        Vector3f eye = new Vector3f(
                (float) (light.x - camera.x),
                (float) (light.y - camera.y),
                (float) (light.z - camera.z));
        Vector3f center = new Vector3f(eye).add(
                light.directionX, light.directionY, light.directionZ);
        Vector3f up = Math.abs(light.directionY) > 0.99f
                ? new Vector3f(0f, 0f, 1f) : new Vector3f(0f, 1f, 0f);
        return new Matrix4f().perspective(fieldOfView, 1f, 0.05f, light.range)
                .lookAt(eye, center, up);
    }

    private static void setupState() {
        GL11.glColorMask(true, true, true, true);
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GL20.glBlendEquation(GL14.GL_FUNC_ADD);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }
}
