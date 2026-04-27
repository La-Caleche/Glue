package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.pipeline.RenderTarget;
import fr.lacaleche.glue.client.utils.FramebufferHelper;
import fr.lacaleche.glue.compat.RenderCompat;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
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
 * Low-level OpenGL draw utilities for Glue's shader system.
 *
 * <p>
 * Provides raw GL quad rendering ({@link #drawQuad}, {@link #drawTexturedQuad})
 * and a depth-aware capture-to-screen blit ({@link #blitCapture}). All methods
 * save and restore GL state via {@link SavedGlState}.
 * </p>
 *
 * <p>
 * <strong>Internal:</strong> consumers should use
 * {@link fr.lacaleche.glue.client.shader.GluePipeline GluePipeline} or
 * {@link fr.lacaleche.glue.client.shader.ShadedBufferSource ShadedBufferSource}
 * instead.
 * </p>
 */
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

    public static void drawQuad(Matrix4f mvpMatrix, float[] vertices, float[] colors, int vertexCount,
            boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_color", "glue_position_color.vsh", "glue_position_color.fsh");
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

    public static void drawTexturedQuad(Matrix4f mvpMatrix, float[] vertices, float[] uvs,
            float[] colors, int textureId, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_tex_color", "glue_position_tex_color.vsh",
                "glue_position_tex_color.fsh");
        SavedGlState state = SavedGlState.save();

        GL20.glUseProgram(program);
        uploadMvpUniform(program, mvpMatrix);

        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        int samplerLoc = GL20.glGetUniformLocation(program, "Sampler");
        if (samplerLoc >= 0)
            GL20.glUniform1i(samplerLoc, 0);

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

    public static void blitCapture(int captureColorId, int captureDepthId, int sceneDepthId, boolean additive) {
        int program = getOrCreateProgram("glue_depth_blit", "glue_depth_blit.vsh", "glue_depth_blit.fsh");
        SavedGlState state = SavedGlState.save();

        setupBlitState(program, additive);
        bindUniforms(program, captureColorId, captureDepthId, sceneDepthId, additive);
        drawFullscreenQuad(program);

        state.restore();
    }

    public static Matrix4f getProjectionMatrix() {
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

    public static void cleanup() {
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

    private static void setupBlitState(int program, boolean additive) {
        RenderTarget mainTarget = Minecraft.getInstance().getMainRenderTarget();
        int mainFboId = FramebufferHelper.getFramebufferId(mainTarget);

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFboId);
        GL20.glDrawBuffers(new int[] { GL30.GL_COLOR_ATTACHMENT0 });
        GL11.glViewport(0, 0, mainTarget.width, mainTarget.height);

        GL20.glUseProgram(program);

        GL11.glEnable(GL11.GL_BLEND);
        if (additive) {
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE);
        } else {
            GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE,
                    GL11.GL_ONE_MINUS_SRC_ALPHA);
        }
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
    }

    private static void bindUniforms(int program, int captureColorId, int captureDepthId,
            int sceneDepthId, boolean additive) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureColorId);
        int loc0 = GL20.glGetUniformLocation(program, "CaptureColor");
        if (loc0 >= 0)
            GL20.glUniform1i(loc0, 0);

        GL13.glActiveTexture(GL13.GL_TEXTURE1);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, captureDepthId);
        int loc1 = GL20.glGetUniformLocation(program, "CaptureDepth");
        if (loc1 >= 0)
            GL20.glUniform1i(loc1, 1);

        int effectiveSceneDepthId = RenderCompat.getIrisMainDepthGlId();
        if (effectiveSceneDepthId <= 0) {
            effectiveSceneDepthId = sceneDepthId;
        }
        boolean hasSceneDepth = effectiveSceneDepthId > 0;
        if (hasSceneDepth) {
            GL13.glActiveTexture(GL13.GL_TEXTURE2);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, effectiveSceneDepthId);
            int loc2 = GL20.glGetUniformLocation(program, "SceneDepth");
            if (loc2 >= 0)
                GL20.glUniform1i(loc2, 2);
        }
        int locHas = GL20.glGetUniformLocation(program, "HasSceneDepth");
        if (locHas >= 0)
            GL20.glUniform1i(locHas, hasSceneDepth ? 1 : 0);

        int locAdditive = GL20.glGetUniformLocation(program, "IsAdditive");
        if (locAdditive >= 0)
            GL20.glUniform1i(locAdditive, additive ? 1 : 0);

        if (!blitLogged) {
            blitLogged = true;
            LOGGER.info("[Glue-Blit] captureColor={}, captureDepth={}, sceneDepth={}, additive={}",
                    captureColorId, captureDepthId, effectiveSceneDepthId, additive);
        }
    }

    private static void drawFullscreenQuad(int program) {
        if (blitVao == 0) {
            blitVao = GL30.glGenVertexArrays();
            blitPosVbo = GL15.glGenBuffers();
            blitUvVbo = GL15.glGenBuffers();

            float[] verts = { -1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f };
            float[] uvs = { 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f };

            GL30.glBindVertexArray(blitVao);

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, blitPosVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, verts, GL15.GL_STATIC_DRAW);
            int posAttrib = GL20.glGetAttribLocation(program, "Position");
            if (posAttrib >= 0) {
                GL20.glEnableVertexAttribArray(posAttrib);
                GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 0, 0);
            }

            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, blitUvVbo);
            GL15.glBufferData(GL15.GL_ARRAY_BUFFER, uvs, GL15.GL_STATIC_DRAW);
            int uvAttrib = GL20.glGetAttribLocation(program, "UV");
            if (uvAttrib >= 0) {
                GL20.glEnableVertexAttribArray(uvAttrib);
                GL20.glVertexAttribPointer(uvAttrib, 2, GL11.GL_FLOAT, false, 0, 0);
            }
        } else {
            GL30.glBindVertexArray(blitVao);
        }

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
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
     * Uses buffer orphaning (glBufferData with same usage hint) for efficient
     * streaming.
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

    private static int getOrCreateProgram(String name, String vertFile, String fragFile) {
        if (programCache.containsKey(name)) {
            return programCache.get(name);
        }
        String vertSource = loadShaderResource(vertFile);
        String fragSource = loadShaderResource(fragFile);
        int program = compileProgram(vertSource, fragSource);
        programCache.put(name, program);
        return program;
    }

    private static String loadShaderResource(String name) {
        try {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("glue", "shaders/internal/" + name);
            ResourceManager rm = Minecraft.getInstance().getResourceManager();
            Optional<Resource> res = rm.getResource(loc);
            if (res.isPresent()) {
                try (InputStream is = res.get().open()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else {
                LOGGER.error("[Glue] Missing internal shader resource: {}", loc);
                return "";
            }
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
}
