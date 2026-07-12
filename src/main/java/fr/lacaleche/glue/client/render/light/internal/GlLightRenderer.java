package fr.lacaleche.glue.client.render.light.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.render.light.Light;
import fr.lacaleche.glue.client.render.light.LightType;
import fr.lacaleche.glue.client.shader.internal.SavedGlState;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Raw-GL engine for the deferred lighting pass. Accumulates each {@link Light}
 * into a lighting buffer with additive blending, then composites that buffer
 * onto the bound scene target.
 *
 * <p>The shader compile/load helpers are copied from
 * {@code GlDirectRenderer} (they are private/static there and its
 * {@code loadShaderResource} is hardcoded to {@code glue:shaders/internal/}).
 * Every public method wraps its GL work in {@link SavedGlState#save()} /
 * {@link SavedGlState#restore()}.</p>
 *
 * <p><strong>Composite trap:</strong> we do NOT reuse
 * {@code GlDirectRenderer.blitCapture} for the final composite &mdash; its
 * {@code glue_depth_blit.fsh} discards where {@code capturedZ >= sceneZ}, and
 * our lighting buffer's depth is cleared to 1.0, so under Iris (which forces
 * {@code HasSceneDepth=1}) the entire buffer would be discarded. A dedicated
 * non-depth-comparing additive composite is used instead.</p>
 */
@Environment(EnvType.CLIENT)
public final class GlLightRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");

    /** Scales the HDR light before tonemapping. */
    private static final float EXPOSURE = 1.4f;
    /**
     * Light returned by a pitch-black surface. The scene colour stands in for albedo,
     * so without a floor a surface that vanilla left unlit -- which is exactly what
     * these lights exist to illuminate -- would reflect nothing.
     */
    private static final float ALBEDO_FLOOR = 0.35f;

    /**
     * Tap spacing of the transmittance blur, in shadow-map texels. The kernel reaches six
     * strides, so this scales how far light spreads crossing glass. It has to clear the
     * border baked into a glass texture (a few texels) to stop that border reading as a
     * hard shadow, but too wide and the coloured light stops respecting the pane's shape.
     */
    private static final float TINT_BLUR_STRIDE = 2.5f;

    private final Map<String, Integer> programCache = new HashMap<>();

    private int quadVao = 0;
    private int quadPosVbo = 0;
    private int quadUvVbo = 0;

    private int scratchFbo = 0;
    private int scratchTex = 0;
    private int scratchSize = 0;

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Additively accumulate a single light into {@code lightFboId}.
     *
     * @param lightFboId  GL framebuffer name of the (pre-cleared) lighting buffer
     * @param sceneDepthId GL texture id of the scene depth buffer to sample
     * @param invViewProj clip-&gt;camera-relative-world reconstruction matrix
     * @param cameraPos   absolute world camera position (for camera-relative light pos)
     * @param light       the light to render
     * @param width       target width in pixels
     * @param height      target height in pixels
     * @param shadow      this light's shadow map, or {@code null} to fall back to
     *                    screen-space contact shadows
     * @param glassColorId glass G-buffer albedo texture, or -1 if no glass this frame
     * @param glassDepthId glass G-buffer depth texture, or -1 if no glass this frame
     */
    public void accumulateLight(int lightFboId, int sceneDepthId, Matrix4f viewProj, Matrix4f invViewProj,
                                Vector3d cameraPos, Light light, int width, int height,
                                @Nullable ShadowParams shadow, int glassColorId, int glassDepthId) {
        int program = getOrCreateProgram("glue_light_deferred",
                "glue_light_deferred.vsh", "glue_light_deferred.fsh");
        if (program == 0 || lightFboId <= 0 || sceneDepthId <= 0) return;

        SavedGlState state = SavedGlState.save();

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, lightFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, width, height);

        GL20.glUseProgram(program);
        setupAdditiveState();

        // Scene depth on unit 0.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneDepthId);
        uniform1i(program, "SceneDepth", 0);

        // Gobo on unit 1 (both within SavedGlState's 0-2 restore range).
        boolean hasGobo = light.type == LightType.GOBO && light.goboTextureId > 0;
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasGobo ? light.goboTextureId : 0);
        uniform1i(program, "Gobo", 1);
        uniform1i(program, "HasGobo", hasGobo ? 1 : 0);

        uniformMat4(program, "InvViewProj", invViewProj);
        uniformMat4(program, "ViewProj", viewProj);
        uniform2f(program, "TexelSize", 1f / width, 1f / height);

        // Camera-relative light position keeps reconstruction in float range.
        float lx = (float) (light.x - cameraPos.x);
        float ly = (float) (light.y - cameraPos.y);
        float lz = (float) (light.z - cameraPos.z);
        uniform3f(program, "LightPos", lx, ly, lz);
        uniform3f(program, "LightColor",
                light.r * light.intensity, light.g * light.intensity, light.b * light.intensity);
        uniform1f(program, "Range", light.range);
        uniform1i(program, "LightType", light.type.ordinal());
        uniform3f(program, "SpotDir", light.direction.x, light.direction.y, light.direction.z);
        uniform1f(program, "CosInner", light.cosInner);
        uniform1f(program, "CosOuter", light.cosOuter);
        uniformMat4(program, "LightMatrix", hasGobo ? buildGoboMatrix(light, cameraPos) : IDENTITY);

        // Shadow map on unit 2 (within SavedGlState's 0-2 restore range).
        boolean hasShadowMap = shadow != null && shadow.textureId() > 0;
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasShadowMap ? shadow.textureId() : 0);
        uniform1i(program, "ShadowMap", 2);
        uniform1i(program, "HasShadowMap", hasShadowMap ? 1 : 0);
        uniformMat4(program, "LightViewProj", hasShadowMap ? shadow.lightViewProj() : IDENTITY);
        uniform1f(program, "ShadowTexel", hasShadowMap && shadow.mapSize() > 0 ? 1f / shadow.mapSize() : 0f);
        uniform1f(program, "ShadowNear", hasShadowMap ? shadow.near() : 0.05f);
        uniform1f(program, "ShadowFar", hasShadowMap ? shadow.far() : 1f);
        uniform1f(program, "ShadowFocalY", hasShadowMap ? shadow.focalY() : 1f);
        uniform1f(program, "LightSize", hasShadowMap ? shadow.lightSize() : 0.1f);
        uniform1i(program, "ShadowFace", hasShadowMap ? shadow.faceAxis() : -1);

        // Glass tint on unit 3: the shadow map's colour attachment, holding how much of
        // each channel survives the trip from the light. White where nothing is in the
        // way, red where the light came through red stained glass. Unit 6 carries the
        // matching WITH-translucents depth map: a receiver shadowed there but lit in the
        // opaque map has a pane between it and the light, and takes the tint.
        boolean hasTint = hasShadowMap && shadow.tintTextureId() > 0 && shadow.tintDepthId() > 0;
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasTint ? shadow.tintTextureId() : 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE6);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasTint ? shadow.tintDepthId() : 0);
        uniform1i(program, "ShadowTint", 3);
        uniform1i(program, "TintDepth", 6);
        uniform1i(program, "HasShadowTint", hasTint ? 1 : 0);

        // Glass G-buffer on units 4-5 (SavedGlState restores 0-5): the nearest pane's
        // albedo and depth from the camera, so the shader can identify glass pixels by
        // depth equality and colour their glow with the pane's own texture.
        boolean hasGlass = glassColorId > 0 && glassDepthId > 0;
        GL13.glActiveTexture(GL13.GL_TEXTURE4);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasGlass ? glassColorId : 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE5);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, hasGlass ? glassDepthId : 0);
        uniform1i(program, "GlassAlbedo", 4);
        uniform1i(program, "GlassDepth", 5);
        uniform1i(program, "HasGlassG", hasGlass ? 1 : 0);

        drawFullscreenQuad(program);

        state.restore();
    }

    /**
     * Additively composite the accumulated lighting buffer onto the currently
     * intended scene target (rebinds the main render target). No depth compare.
     *
     * @param sceneColorTexId copy of the scene colour, used as a stand-in for albedo
     *                        so the light is tinted by whatever surface it lands on
     */
    public void compositeAdditive(int lightColorTexId, int sceneColorTexId, int width, int height) {
        int program = getOrCreateProgram("glue_light_composite",
                "glue_light_deferred.vsh", "glue_light_composite.fsh");
        if (program == 0 || lightColorTexId <= 0 || sceneColorTexId <= 0) return;

        SavedGlState state = SavedGlState.save();

        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, FramebufferHelper.getFramebufferId(main));
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, width, height);

        GL20.glUseProgram(program);
        setupAdditiveState();

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, lightColorTexId);
        uniform1i(program, "LightTex", 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sceneColorTexId);
        uniform1i(program, "SceneTex", 1);

        uniform1f(program, "Exposure", EXPOSURE);
        uniform1f(program, "AlbedoFloor", ALBEDO_FLOOR);

        drawFullscreenQuad(program);

        state.restore();
    }

    /**
     * Blur a shadow map's transmittance attachment in place, separably.
     *
     * <p>Called once when the map is baked (and the maps are cached), so it can afford a
     * radius wide enough to dissolve the near-opaque border baked into every glass
     * texture &mdash; which otherwise shows up as a hard cross where four block faces
     * meet, stamped onto the glass's own lit face and projected onto the floor. Doing
     * this per pixel per frame instead can only make that cross noisy, not remove it.</p>
     *
     * @param tintFboId GL framebuffer whose colour attachment is {@code tintTexId}
     * @param tintTexId the transmittance texture, read and written
     * @param size      the map's resolution (square)
     */
    public void blurTint(int tintFboId, int tintTexId, int size) {
        int program = getOrCreateProgram("glue_light_tint_blur",
                "glue_light_deferred.vsh", "glue_light_tint_blur.fsh");
        if (program == 0 || tintFboId <= 0 || tintTexId <= 0 || size <= 0) return;

        SavedGlState state = SavedGlState.save();
        ensureScratch(size);
        if (scratchFbo == 0) {
            state.restore();
            return;
        }

        GL20.glUseProgram(program);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glViewport(0, 0, size, size);

        // Linear filtering: the taps land between texels, and a blur wants them blended.
        // Clamped: the kernel steps off the edge of the map at its borders, and a
        // repeating wrap would fold the opposite side of the shadow map back in.
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tintTexId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        float step = TINT_BLUR_STRIDE / (float) size;

        // Horizontal: tint -> scratch.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFbo);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        uniform1i(program, "Source", 0);
        uniform2f(program, "Direction", step, 0f);
        drawFullscreenQuad(program);

        // Vertical: scratch -> tint.
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, tintFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchTex);
        uniform1i(program, "Source", 0);
        uniform2f(program, "Direction", 0f, step);
        drawFullscreenQuad(program);

        state.restore();
    }

    private void ensureScratch(int size) {
        // EXACT size, not ">= size". The blur ping-pongs through this texture using
        // normalised [0,1] coords, so a scratch that is merely big enough is wrong: the
        // horizontal pass fills a size x size CORNER of it, and the vertical pass then
        // reads the full texture and samples mostly uninitialised memory. Spot maps are
        // 1024 and point-light faces are 512, so an oversized scratch is the common case,
        // not an edge case. Reallocating is fine -- bakes are rare, and cached.
        if (scratchFbo != 0 && scratchSize == size) return;
        releaseScratch();
        scratchSize = size;

        scratchTex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, scratchTex);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, size, size, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        scratchFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, scratchFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
                GL11.GL_TEXTURE_2D, scratchTex, 0);

        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.error("[Glue] Tint blur scratch FBO incomplete: 0x{}", Integer.toHexString(status));
            releaseScratch();
        }
    }

    private void releaseScratch() {
        if (scratchFbo != 0) {
            GL30.glDeleteFramebuffers(scratchFbo);
            scratchFbo = 0;
        }
        if (scratchTex != 0) {
            GL11.glDeleteTextures(scratchTex);
            scratchTex = 0;
        }
        scratchSize = 0;
    }

    public void cleanup() {
        for (int program : programCache.values()) {
            if (program != 0) GL20.glDeleteProgram(program);
        }
        programCache.clear();
        releaseScratch();
        if (quadVao != 0) {
            GL30.glDeleteVertexArrays(quadVao);
            GL15.glDeleteBuffers(quadPosVbo);
            GL15.glDeleteBuffers(quadUvVbo);
            quadVao = quadPosVbo = quadUvVbo = 0;
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static final Matrix4f IDENTITY = new Matrix4f();

    /** Build a world(camera-relative)-&gt;gobo-clip projection from the spot cone. */
    private static Matrix4f buildGoboMatrix(Light light, Vector3d cameraPos) {
        float halfAngle = (float) Math.acos(Math.max(-1f, Math.min(1f, light.cosOuter)));
        float fov = Math.max(0.1f, Math.min((float) Math.PI - 0.1f, halfAngle * 2f));
        org.joml.Vector3f eye = new org.joml.Vector3f(
                (float) (light.x - cameraPos.x),
                (float) (light.y - cameraPos.y),
                (float) (light.z - cameraPos.z));
        org.joml.Vector3f center = new org.joml.Vector3f(eye).add(light.direction);
        org.joml.Vector3f up = Math.abs(light.direction.y) > 0.99f
                ? new org.joml.Vector3f(0f, 0f, 1f)
                : new org.joml.Vector3f(0f, 1f, 0f);
        return new Matrix4f()
                .perspective(fov, 1f, 0.05f, light.range)
                .lookAt(eye, center, up);
    }

    private void setupAdditiveState() {
        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private void drawFullscreenQuad(int program) {
        if (quadVao == 0) {
            quadVao = GL30.glGenVertexArrays();
            quadPosVbo = GL15.glGenBuffers();
            quadUvVbo = GL15.glGenBuffers();

            float[] verts = {-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f};
            float[] uvs = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};

            GL30.glBindVertexArray(quadVao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadPosVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadUvVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvs, GL15.GL_STATIC_DRAW);
        }

        GL30.glBindVertexArray(quadVao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadPosVbo);
        int posAttrib = GL20.glGetAttribLocation(program, "Position");
        if (posAttrib >= 0) {
            GL20.glEnableVertexAttribArray(posAttrib);
            GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 0, 0);
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadUvVbo);
        int uvAttrib = GL20.glGetAttribLocation(program, "UV");
        if (uvAttrib >= 0) {
            GL20.glEnableVertexAttribArray(uvAttrib);
            GL20.glVertexAttribPointer(uvAttrib, 2, GL11.GL_FLOAT, false, 0, 0);
        }

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
    }

    private void uniform1i(int program, String name, int value) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform1i(loc, value);
    }

    private void uniform1f(int program, String name, float value) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform1f(loc, value);
    }

    private void uniform2f(int program, String name, float x, float y) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform2f(loc, x, y);
    }

    private void uniform3f(int program, String name, float x, float y, float z) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc >= 0) GL20.glUniform3f(loc, x, y, z);
    }

    private void uniformMat4(int program, String name, Matrix4f matrix) {
        int loc = GL20.glGetUniformLocation(program, name);
        if (loc < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.mallocFloat(16);
            matrix.get(buf);
            GL20.glUniformMatrix4fv(loc, false, buf);
        }
    }

    private int getOrCreateProgram(String name, String vertFile, String fragFile) {
        if (programCache.containsKey(name)) {
            return programCache.get(name);
        }
        int program = 0;
        try {
            program = compileProgram(loadShaderResource(vertFile), loadShaderResource(fragFile));
        } catch (RuntimeException e) {
            LOGGER.error("[Glue] Light shader '{}' failed to compile, lighting disabled for this program", name, e);
        }
        programCache.put(name, program);
        return program;
    }

    // --- copied from GlDirectRenderer (private/static there) ---

    private static String loadShaderResource(String name) {
        try {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("glue", "shaders/internal/" + name);
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            LOGGER.error("[Glue] Missing internal shader resource: {}", loc);
            return "";
        } catch (Exception e) {
            LOGGER.error("[Glue] Failed to load internal shader resource: {}", name, e);
            return "";
        }
    }

    private static int compileProgram(String vertSource, String fragSource) {
        int vert = compileShader(GL20.GL_VERTEX_SHADER, vertSource);
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, fragSource);

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vert);
        GL20.glAttachShader(program, frag);
        GL20.glLinkProgram(program);

        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetProgramInfoLog(program);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vert);
            GL20.glDeleteShader(frag);
            throw new RuntimeException("Failed to link Glue light program: " + log);
        }

        GL20.glDeleteShader(vert);
        GL20.glDeleteShader(frag);
        return program;
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("Failed to compile Glue light shader: " + log);
        }

        return shader;
    }
}
