package fr.lacaleche.glue.client.shader;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
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

@Environment(EnvType.CLIENT)
public class GlDirectRenderer {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue-blit");
    private static final Map<String, Integer> programCache = new HashMap<>();
    private static boolean blitLogged = false;
    private static boolean projectionWarned = false;

    private static int colorQuadVao = 0;
    private static int colorQuadPosVbo = 0;
    private static int colorQuadColorVbo = 0;

    private static int texQuadVao = 0;
    private static int texQuadPosVbo = 0;
    private static int texQuadUvVbo = 0;
    private static int texQuadColorVbo = 0;

    private static int blitVao = 0;
    private static int blitPosVbo = 0;
    private static int blitUvVbo = 0;

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

    static void drawQuad(Matrix4f mvpMatrix, float[] vertices, float[] colors, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_color", VERT_POSITION_COLOR, FRAG_POSITION_COLOR);
        SavedGlState state = SavedGlState.save();

        GL20.glUseProgram(program);
        uploadMvpUniform(program, mvpMatrix);

        if (colorQuadVao == 0) {
            colorQuadVao = GL30.glGenVertexArrays();
            colorQuadPosVbo = GL15.glGenBuffers();
            colorQuadColorVbo = GL15.glGenBuffers();
        }
        GL30.glBindVertexArray(colorQuadVao);

        updateAttrib(program, "Position", colorQuadPosVbo, vertices, 3);
        updateAttrib(program, "Color", colorQuadColorVbo, colors, 4);

        setupBlendAndDepth(useDepthTest);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);

        state.restore();
    }

    static void drawTexturedQuad(Matrix4f mvpMatrix, float[] vertices, float[] uvs,
                                 float[] colors, int textureId, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_tex_color", VERT_POSITION_TEX_COLOR, FRAG_POSITION_TEX_COLOR);
        SavedGlState state = SavedGlState.save();

        GL20.glUseProgram(program);
        uploadMvpUniform(program, mvpMatrix);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int samplerLoc = GL20.glGetUniformLocation(program, "Sampler");
        if (samplerLoc >= 0) GL20.glUniform1i(samplerLoc, 0);

        if (texQuadVao == 0) {
            texQuadVao = GL30.glGenVertexArrays();
            texQuadPosVbo = GL15.glGenBuffers();
            texQuadUvVbo = GL15.glGenBuffers();
            texQuadColorVbo = GL15.glGenBuffers();
        }
        GL30.glBindVertexArray(texQuadVao);

        updateAttrib(program, "Position", texQuadPosVbo, vertices, 3);
        updateAttrib(program, "UV", texQuadUvVbo, uvs, 2);
        updateAttrib(program, "Color", texQuadColorVbo, colors, 4);

        setupBlendAndDepth(useDepthTest);
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);

        state.restore();
    }

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

                if (HasSceneDepth == 1) {
                    float capturedZ = texture(CaptureDepth, texCoord).r;
                    float sceneZ = texture(SceneDepth, texCoord).r;
                    if (capturedZ >= sceneZ) discard;
                }

                fragColor = color;
            }
            """;

    public static void blitCapture(int captureColorId, int captureDepthId, int sceneDepthId, boolean additive) {
        int program = getOrCreateProgram("glue_depth_blit", VERT_BLIT, FRAG_DEPTH_BLIT);
        SavedGlState state = SavedGlState.save();

        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int mainFboId = FramebufferHelper.getFramebufferId(mainTarget);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFboId);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0});
        GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);

        GL20.glUseProgram(program);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureColorId);
        int loc0 = GL20.glGetUniformLocation(program, "CaptureColor");
        if (loc0 >= 0) GL20.glUniform1i(loc0, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureDepthId);
        int loc1 = GL20.glGetUniformLocation(program, "CaptureDepth");
        if (loc1 >= 0) GL20.glUniform1i(loc1, 1);

        int irisDepthId = RenderCompat.getIrisMainDepthGlId();
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
            LOGGER.info("[Glue-Blit] captureColor={}, captureDepth={}, irisDepth={}, mainFBO={}, additive={}",
                    captureColorId, captureDepthId, irisDepthId, mainFboId, additive);
        }

        float[] verts = { -1f,-1f,0f,  1f,-1f,0f,  1f,1f,0f,  -1f,1f,0f };
        float[] uvs   = {  0f,0f,       1f,0f,      1f,1f,      0f,1f    };

        if (blitVao == 0) {
            blitVao = GL30.glGenVertexArrays();
            blitPosVbo = GL15.glGenBuffers();
            blitUvVbo = GL15.glGenBuffers();
        }
        GL30.glBindVertexArray(blitVao);
        updateAttrib(program, "Position", blitPosVbo, verts, 3);
        updateAttrib(program, "UV", blitUvVbo, uvs, 2);

        GL11.glEnable(GL11.GL_BLEND);
        if (additive) {
            // Additive blit: result = src + dst (pure additive)
            // Black pixels add nothing, bright pixels glow.
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        } else {
            // Alpha blit: result = src.a * src + (1 - src.a) * dst
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        state.restore();
    }

    static Matrix4f getProjectionMatrix() {
        Minecraft mc = Minecraft.getInstance();
        float fov = mc.options.fov().get().floatValue();
        try {
            fov = mc.gameRenderer.getFov(mc.gameRenderer.getMainCamera(),
                    mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), true);
        } catch (Exception e) {
            if (!projectionWarned) {
                projectionWarned = true;
                LOGGER.warn("[Glue] Failed to compute camera FOV, falling back to options value", e);
            }
        }
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

    /**
     * Updates an existing VBO with new data and sets up the vertex attribute.
     * Uses buffer orphaning (glBufferData with same usage hint) for efficient streaming.
     */
    private static void updateAttrib(int program, String name, int vbo, float[] data, int components) {
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, data, GL15.GL_STREAM_DRAW);

        int attrib = GL20.glGetAttribLocation(program, name);
        if (attrib >= 0) {
            GL20.glEnableVertexAttribArray(attrib);
            GL20.glVertexAttribPointer(attrib, components, GL11.GL_FLOAT, false, 0, 0);
        }
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

        if (colorQuadVao != 0) {
            GL30.glDeleteVertexArrays(colorQuadVao);
            GL15.glDeleteBuffers(colorQuadPosVbo);
            GL15.glDeleteBuffers(colorQuadColorVbo);
            colorQuadVao = colorQuadPosVbo = colorQuadColorVbo = 0;
        }
        if (texQuadVao != 0) {
            GL30.glDeleteVertexArrays(texQuadVao);
            GL15.glDeleteBuffers(texQuadPosVbo);
            GL15.glDeleteBuffers(texQuadUvVbo);
            GL15.glDeleteBuffers(texQuadColorVbo);
            texQuadVao = texQuadPosVbo = texQuadUvVbo = texQuadColorVbo = 0;
        }
        if (blitVao != 0) {
            GL30.glDeleteVertexArrays(blitVao);
            GL15.glDeleteBuffers(blitPosVbo);
            GL15.glDeleteBuffers(blitUvVbo);
            blitVao = blitPosVbo = blitUvVbo = 0;
        }
    }

    private record SavedGlState(
            int program, int fbo, int vao,
            boolean blend, int blendSrcRgb, int blendDstRgb, int blendSrcAlpha, int blendDstAlpha,
            boolean depth, boolean depthWrite, int depthFunc,
            boolean cull,
            int activeTexture,
            int[] viewport
    ) {
        static SavedGlState save() {
            int[] vp = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
            return new SavedGlState(
                    GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM),
                    GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING),
                    GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING),
                    GL11.glIsEnabled(GL11.GL_BLEND),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_RGB),
                    GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA),
                    GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA),
                    GL11.glIsEnabled(GL11.GL_DEPTH_TEST),
                    GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE),
                    vp
            );
        }

        void restore() {
            GL20.glUseProgram(program);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
            GL30.glBindVertexArray(vao);
            if (depth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(depthFunc);
            GL11.glDepthMask(depthWrite);
            if (blend) {
                GL11.glEnable(GL11.GL_BLEND);
                GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            } else {
                GL11.glDisable(GL11.GL_BLEND);
            }
            if (cull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            GL13.glActiveTexture(activeTexture);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        }
    }
}
