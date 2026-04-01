package fr.lacaleche.glue.client.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * Raw OpenGL renderer that bypasses MC's pipeline and all Iris hooks.
 * Compiles inline GLSL, caches programs, renders quads via direct GL calls.
 */
@Environment(EnvType.CLIENT)
class GlDirectRenderer {

    private static final Map<String, Integer> programCache = new HashMap<>();

    private static final String VERTEX_SHADER_SOURCE = """
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

    private static final String FRAGMENT_SHADER_SOURCE = """
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

    static void drawQuad(Matrix4f mvpMatrix, float[] vertices, float[] colors, int vertexCount, boolean useDepthTest) {
        int program = getOrCreateProgram("glue_position_color");

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int previousFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean blendWasEnabled = GL11.glIsEnabled(GL11.GL_BLEND);
        int previousBlendSrc = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
        int previousBlendDst = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        boolean depthTestWasEnabled = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWriteWas = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean cullWasEnabled = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousDepthFunc = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int previousVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);

        GL20.glUseProgram(program);

        int mvpLoc = GL20.glGetUniformLocation(program, "MVP");
        if (mvpLoc >= 0) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                FloatBuffer matBuf = stack.mallocFloat(16);
                mvpMatrix.get(matBuf);
                GL20.glUniformMatrix4fv(mvpLoc, false, matBuf);
            }
        }

        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        int posVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, posVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertices, GL15.GL_STREAM_DRAW);

        int posAttrib = GL20.glGetAttribLocation(program, "Position");
        if (posAttrib >= 0) {
            GL20.glEnableVertexAttribArray(posAttrib);
            GL20.glVertexAttribPointer(posAttrib, 3, GL11.GL_FLOAT, false, 0, 0);
        }

        int colorVbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, colorVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, colors, GL15.GL_STREAM_DRAW);

        int colorAttrib = GL20.glGetAttribLocation(program, "Color");
        if (colorAttrib >= 0) {
            GL20.glEnableVertexAttribArray(colorAttrib);
            GL20.glVertexAttribPointer(colorAttrib, 4, GL11.GL_FLOAT, false, 0, 0);
        }

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

        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, vertexCount);

        GL30.glBindVertexArray(previousVao);
        GL15.glDeleteBuffers(posVbo);
        GL15.glDeleteBuffers(colorVbo);
        GL30.glDeleteVertexArrays(vao);

        GL20.glUseProgram(previousProgram);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, previousFbo);
        if (depthTestWasEnabled) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthFunc(previousDepthFunc);
        GL11.glDepthMask(depthWriteWas);
        if (blendWasEnabled) {
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(previousBlendSrc, previousBlendDst);
        } else {
            GL11.glDisable(GL11.GL_BLEND);
        }
        if (cullWasEnabled) GL11.glEnable(GL11.GL_CULL_FACE);
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

    private static int getOrCreateProgram(String name) {
        return programCache.computeIfAbsent(name, k -> compileProgram());
    }

    private static int compileProgram() {
        int vert = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
        int frag = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);

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
}
