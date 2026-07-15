package fr.lacaleche.glue.client.render.light.internal.gl;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Shared shader programs and fullscreen geometry for all Lumos GL passes. */
public final class GlLightResources {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/light");

    private final Map<String, Integer> programs = new HashMap<>();
    private int quadVao;
    private int quadPositionBuffer;
    private int quadUvBuffer;

    int program(String name, String vertexFile, String fragmentFile) {
        if (programs.containsKey(name)) return programs.get(name);
        int program = 0;
        try {
            program = compileProgram(loadShader(vertexFile), loadShader(fragmentFile));
        } catch (RuntimeException exception) {
            LOGGER.error("[Glue] Light shader '{}' failed to compile", name, exception);
        }
        programs.put(name, program);
        return program;
    }

    void drawFullscreen(int program) {
        if (quadVao == 0) createFullscreenQuad();
        GL30.glBindVertexArray(quadVao);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadPositionBuffer);
        int position = GL20.glGetAttribLocation(program, "Position");
        if (position >= 0) {
            GL20.glEnableVertexAttribArray(position);
            GL20.glVertexAttribPointer(position, 3, GL11.GL_FLOAT, false, 0, 0);
        }

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadUvBuffer);
        int uv = GL20.glGetAttribLocation(program, "UV");
        if (uv >= 0) {
            GL20.glEnableVertexAttribArray(uv);
            GL20.glVertexAttribPointer(uv, 2, GL11.GL_FLOAT, false, 0, 0);
        }
        GL11.glDrawArrays(GL11.GL_TRIANGLE_FAN, 0, 4);
    }

    void uniform1i(int program, String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1i(location, value);
    }

    void uniform1f(int program, String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform1f(location, value);
    }

    void uniform2f(int program, String name, float x, float y) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform2f(location, x, y);
    }

    void uniform3f(int program, String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform3f(location, x, y, z);
    }

    void uniform4fv(int program, String name, FloatBuffer values) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location >= 0) GL20.glUniform4fv(location, values);
    }

    void uniformMatrix(int program, String name, Matrix4f matrix) {
        int location = GL20.glGetUniformLocation(program, name);
        if (location < 0) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buffer = stack.mallocFloat(16);
            matrix.get(buffer);
            GL20.glUniformMatrix4fv(location, false, buffer);
        }
    }

    public void cleanup() {
        for (int program : programs.values()) {
            if (program != 0) GL20.glDeleteProgram(program);
        }
        programs.clear();
        if (quadVao != 0) {
            GL30.glDeleteVertexArrays(quadVao);
            GL15.glDeleteBuffers(quadPositionBuffer);
            GL15.glDeleteBuffers(quadUvBuffer);
            quadVao = quadPositionBuffer = quadUvBuffer = 0;
        }
    }

    private void createFullscreenQuad() {
        quadVao = GL30.glGenVertexArrays();
        quadPositionBuffer = GL15.glGenBuffers();
        quadUvBuffer = GL15.glGenBuffers();

        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadPositionBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                new float[]{-1f, -1f, 0f, 1f, -1f, 0f, 1f, 1f, 0f, -1f, 1f, 0f},
                GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadUvBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER,
                new float[]{0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f}, GL15.GL_STATIC_DRAW);
    }

    private static String loadShader(String name) {
        try {
            ResourceLocation location = ResourceLocation.fromNamespaceAndPath(
                    "glue", "shaders/internal/" + name);
            ResourceManager manager = Minecraft.getInstance().getResourceManager();
            Optional<Resource> resource = manager.getResource(location);
            if (resource.isPresent()) {
                try (InputStream input = resource.get().open()) {
                    return new String(input.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
            throw new IllegalStateException("Missing internal shader resource: " + location);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to load internal shader resource: " + name, exception);
        }
    }

    private static int compileProgram(String vertexSource, String fragmentSource) {
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compileShader(GL20.GL_VERTEX_SHADER, vertexSource);
            fragment = compileShader(GL20.GL_FRAGMENT_SHADER, fragmentSource);
            int program = GL20.glCreateProgram();
            GL20.glAttachShader(program, vertex);
            GL20.glAttachShader(program, fragment);
            GL20.glLinkProgram(program);
            if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
                String log = GL20.glGetProgramInfoLog(program);
                GL20.glDeleteProgram(program);
                throw new IllegalStateException("Failed to link Glue light program: " + log);
            }
            return program;
        } finally {
            if (vertex != 0) GL20.glDeleteShader(vertex);
            if (fragment != 0) GL20.glDeleteShader(fragment);
        }
    }

    private static int compileShader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String log = GL20.glGetShaderInfoLog(shader);
            GL20.glDeleteShader(shader);
            throw new IllegalStateException("Failed to compile Glue light shader: " + log);
        }
        return shader;
    }
}
