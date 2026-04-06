package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Raw OpenGL renderer that bypasses MC's pipeline and all Iris hooks.
 * Compiles inline GLSL, caches programs, renders quads via direct GL calls.
 *
 * <p>Supports two drawing modes:</p>
 * <ul>
 *   <li><b>Position + Color</b> — flat colored quads (gradient, solid)</li>
 *   <li><b>Position + UV + Color</b> — textured quads with optional tint (FBO captures, sprites)</li>
 * </ul>
 */
@Environment(EnvType.CLIENT)
class GlDirectRenderer {

    private static final Map<String, Integer> programCache = new HashMap<>();

    // ── Position + Color shader ──────────────────────────────────

    private static final String VERT_POSITION_COLOR = """
            #version 150

            uniform mat4 MVP;

            in vec3 Position;
            in vec4 Color;

            out vec4 vertexColor;

            void main() {
                gl_Position = MVP * vec4(Position, 1.0);
                vertexColor = Color;
            }
            """;

    private static final String FRAG_POSITION_COLOR = """
            #version 150

            in vec4 vertexColor;

            out vec4 fragColor;

            void main() {
                vec4 color = vertexColor;
                if (color.a == 0.0) {
                    discard;
                }
                fragColor = color;
            }
            """;

    // ── Position + UV + Color shader ─────────────────────────────

    private static final String VERT_POSITION_TEX_COLOR = """
            #version 150

            uniform mat4 MVP;

            in vec3 Position;
            in vec2 UV;
            in vec4 Color;

            out vec2 texCoord;
            out vec4 vertexColor;

            void main() {
                gl_Position = MVP * vec4(Position, 1.0);
                texCoord = UV;
                vertexColor = Color;
            }
            """;

    private static final String FRAG_POSITION_TEX_COLOR = """
            #version 150

            uniform sampler2D Sampler;

            in vec2 texCoord;
            in vec4 vertexColor;

            out vec4 fragColor;

            void main() {
                vec4 texColor = texture(Sampler, texCoord);
                vec4 color = texColor * vertexColor;
                if (color.a == 0.0) {
                    discard;
                }
                fragColor = color;
            }
            """;

    // ── Flat colored quad ────────────────────────────────────────

    static void drawQuad(Matrix4f mvpMatrix, float[] vertices, float[] colors, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_color", VERT_POSITION_COLOR, FRAG_POSITION_COLOR);
        SavedGlState state = SavedGlState.save();

        GL20.glUseProgram(program);
        uploadMvpUniform(program, mvpMatrix);

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        int posVbo = uploadAttrib(program, "Position", vertices, 3);
        int colorVbo = uploadAttrib(program, "Color", colors, 4);

        setupBlendAndDepth(useDepthTest);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);

        GL30.glBindVertexArray(state.vao);
        GL15.glDeleteBuffers(posVbo);
        GL15.glDeleteBuffers(colorVbo);
        GL30.glDeleteVertexArrays(vao);
        state.restore();
    }

    // ── Textured quad ────────────────────────────────────────────

    static void drawTexturedQuad(Matrix4f mvpMatrix, float[] vertices, float[] uvs,
                                 float[] colors, int textureId, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_tex_color", VERT_POSITION_TEX_COLOR, FRAG_POSITION_TEX_COLOR);
        SavedGlState state = SavedGlState.save();

        GL20.glUseProgram(program);
        uploadMvpUniform(program, mvpMatrix);

        // Bind the texture
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int samplerLoc = GL20.glGetUniformLocation(program, "Sampler");
        if (samplerLoc >= 0) GL20.glUniform1i(samplerLoc, 0);

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        int posVbo = uploadAttrib(program, "Position", vertices, 3);
        int uvVbo = uploadAttrib(program, "UV", uvs, 2);
        int colorVbo = uploadAttrib(program, "Color", colors, 4);

        setupBlendAndDepth(useDepthTest);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);

        GL30.glBindVertexArray(state.vao);
        GL15.glDeleteBuffers(posVbo);
        GL15.glDeleteBuffers(uvVbo);
        GL15.glDeleteBuffers(colorVbo);
        GL30.glDeleteVertexArrays(vao);
        state.restore();
    }

    // ── Depth-aware FBO blit ────────────────────────────────────

    private static final String VERT_BLIT = """
            #version 150

            in vec3 Position;
            in vec2 UV;

            out vec2 texCoord;

            void main() {
                gl_Position = vec4(Position, 1.0);
                texCoord = UV;
            }
            """;

    private static final String FRAG_DEPTH_BLIT = """
            #version 150

            uniform sampler2D CaptureColor;
            uniform sampler2D CaptureDepth;
            uniform sampler2D SceneDepth;
            uniform int HasSceneDepth;

            in vec2 texCoord;
            out vec4 fragColor;

            void main() {
                vec4 color = texture(CaptureColor, texCoord);
                if (color.a < 0.005) discard;

                // Depth-based occlusion: discard if behind scene geometry (includes hand)
                if (HasSceneDepth == 1) {
                    float capturedZ = texture(CaptureDepth, texCoord).r;
                    float sceneZ = texture(SceneDepth, texCoord).r;
                    if (capturedZ >= sceneZ) discard;
                }

                // Un-premultiply alpha and preserve transparency
                float alpha = sqrt(color.a);
                vec3 rgb = color.rgb / max(alpha, 0.001);

                fragColor = vec4(rgb, alpha);
            }
            """;

    /**
     * Blits a captured FBO to MC's main render target with depth occlusion.
     * Scene depth is read from the CURRENT FBO (Iris's, different from draw target),
     * avoiding the GL read-write texture hazard.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-blit");
    private static boolean blitLogged = false;

    static void blitCapture(int captureColorId, int captureDepthId, int sceneDepthId) {
        int program = getOrCreateProgram("glue_depth_blit", VERT_BLIT, FRAG_DEPTH_BLIT);
        SavedGlState state = SavedGlState.save();

        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int mainFboId = FramebufferHelper.getFramebufferId(mainTarget);


        // Draw to MC's main render target (FBO 3) for opaque output
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);

        GL20.glUseProgram(program);

        // TU0: capture color
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureColorId);
        int loc0 = GL20.glGetUniformLocation(program, "CaptureColor");
        if (loc0 >= 0) GL20.glUniform1i(loc0, 0);

        // TU1: capture depth (our item's depth)
        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureDepthId);
        int loc1 = GL20.glGetUniformLocation(program, "CaptureDepth");
        if (loc1 >= 0) GL20.glUniform1i(loc1, 1);

        // TU2: Iris scene depth (includes hand) for shader-based occlusion
        int irisDepthId = getIrisMainDepthGlId();
        boolean hasSceneDepth = irisDepthId > 0;
        if (hasSceneDepth) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, irisDepthId);
            int loc2 = GL20.glGetUniformLocation(program, "SceneDepth");
            if (loc2 >= 0) GL20.glUniform1i(loc2, 2);
        }
        int locHas = GL20.glGetUniformLocation(program, "HasSceneDepth");
        if (locHas >= 0) GL20.glUniform1i(locHas, hasSceneDepth ? 1 : 0);

        if (!blitLogged) {
            blitLogged = true;
            LOGGER.info("[Glue-Blit] captureColor={}, captureDepth={}, irisDepth={}, mainFBO={}",
                    captureColorId, captureDepthId, irisDepthId, mainFboId);
        }

        // Fullscreen quad (NDC)
        float[] verts = { -1f,-1f,0f,  1f,-1f,0f,  1f,1f,0f,  -1f,1f,0f };
        float[] uvs   = {  0f,0f,       1f,0f,      1f,1f,      0f,1f    };

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int posVbo = uploadAttrib(program, "Position", verts, 3);
        int uvVbo  = uploadAttrib(program, "UV", uvs, 2);

        GL11.glEnable(GL11.GL_BLEND);
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        // Cleanup
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        GL30.glBindVertexArray(state.vao);
        GL15.glDeleteBuffers(posVbo);
        GL15.glDeleteBuffers(uvVbo);
        GL30.glDeleteVertexArrays(vao);
        state.restore();
    }

    /**
     * Gets Iris's main depth texture GL ID (includes hand depth).
     * Returns -1 if Iris is not active or reflection fails.
     */
    private static int getIrisMainDepthGlId() {
        try {
            var irisClass = Class.forName("net.irisshaders.iris.Iris");
            var mgr = irisClass.getMethod("getPipelineManager").invoke(null);
            var pipeline = mgr.getClass().getMethod("getPipelineNullable").invoke(mgr);
            if (pipeline == null) return -1;

            var pipeClass = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
            if (!pipeClass.isInstance(pipeline)) return -1;

            var rtsClass = Class.forName("net.irisshaders.iris.targets.RenderTargets");
            java.lang.reflect.Field rtsField = pipeClass.getDeclaredField("renderTargets");
            rtsField.setAccessible(true);
            Object rts = rtsField.get(pipeline);

            // getDepthTexture() returns GpuTexture (which is GlTexture via mixin)
            var depthTex = rtsClass.getMethod("getDepthTexture").invoke(rts);
            if (depthTex == null) return -1;

            // GlTexture.glId() returns the raw GL texture name
            var glTexClass = Class.forName("com.mojang.blaze3d.opengl.GlTexture");
            if (glTexClass.isInstance(depthTex)) {
                return (int) glTexClass.getMethod("glId").invoke(depthTex);
            }
        } catch (Exception ignored) {}
        return -1;
    }

    // ── Shared utilities ─────────────────────────────────────────

    /**
     * Query the depth texture attached to a GL FBO by its ID.
     * Returns -1 if the attachment is a renderbuffer (can't be sampled).
     */
    private static int getDepthTextureFromFbo(int fboId) {
        if (fboId <= 0) return -1;
        int prevRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, fboId);

        int objType = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        int objName = GL30.glGetFramebufferAttachmentParameteri(
                GL30.GL_READ_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);

        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, prevRead);

        boolean isTexture = (objType == GL11.GL_TEXTURE);
        LOGGER.info("[Glue-Depth] FBO {} depth: type={} (texture={}), name={}",
                fboId, objType == GL11.GL_TEXTURE ? "TEXTURE" :
                       objType == GL30.GL_RENDERBUFFER ? "RENDERBUFFER" : "0x" + Integer.toHexString(objType),
                isTexture, objName);

        if (!isTexture || objName <= 0) return -1;
        return objName;
    }

    static Matrix4f getProjectionMatrix() {
        Minecraft mc = Minecraft.getInstance();
        float fov = mc.options.fov().get().floatValue();
        try {
            fov = mc.gameRenderer.getFov(mc.gameRenderer.getMainCamera(),
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        } catch (Exception ignored) {}
        return mc.gameRenderer.getProjectionMatrix(fov);
    }

    private static void uploadMvpUniform(int program, Matrix4f mvp) {
        int loc = GL20.glGetUniformLocation(program, "MVP");
        if (loc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer buf = stack.mallocFloat(16);
                mvp.get(buf);
                GL20.glUniformMatrix4fv(loc, false, buf);
            }
        }
    }

    private static int uploadAttrib(int program, String name, float[] data, int components) {
        int vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);

        int attrib = GL20.glGetAttribLocation(program, name);
        if (attrib >= 0) {
            GL20.glEnableVertexAttribArray(attrib);
            GL20.glVertexAttribPointer(attrib, components, GL11.GL_FLOAT, false, 0, 0);
        }
        return vbo;
    }

    private static void setupBlendAndDepth(boolean useDepthTest) {
        if (useDepthTest) {
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(GL11.GL_LEQUAL);
        } else {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
        }
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    // ── Program cache ────────────────────────────────────────────

    private static int getOrCreateProgram(String name, String vertSource, String fragSource) {
        return programCache.computeIfAbsent(name, k -> compileProgram(vertSource, fragSource));
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
            throw new RuntimeException("Failed to link Glue shader program: " + log);
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
            throw new RuntimeException("Failed to compile Glue shader: " + log);
        }

        return shader;
    }

    static void cleanup() {
        for (int program : programCache.values()) {
            GL20.glDeleteProgram(program);
        }
        programCache.clear();
    }

    // ── GL state save/restore ────────────────────────────────────

    private record SavedGlState(
            int program, int fbo, int vao,
            boolean blend, int blendSrc, int blendDst,
            boolean depth, boolean depthWrite, int depthFunc,
            boolean cull
    ) {
        static SavedGlState save() {
            return new SavedGlState(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE)
            );
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthMask(depthWrite);
            if (blend) {
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glBlendFunc(blendSrc, blendDst);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE);
        }
    }
}
