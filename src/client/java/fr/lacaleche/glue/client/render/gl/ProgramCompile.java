package fr.lacaleche.glue.client.render.gl;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GLCapabilities;

import java.util.ArrayList;
import java.util.List;

/**
 * One compile and link of a GL program from a vertex and a fragment source, which a frame can ask about without
 * waiting. Where the driver compiles in the background ({@code GL_KHR_parallel_shader_compile} or
 * {@code GL_ARB_parallel_shader_compile}), the work is handed to it at once and {@link #ready()} asks whether it is
 * done. Elsewhere it is done on the render thread, at most one every {@link #BLOCKING_SPACING_NS} across every
 * compile, so that compiles started together spread over frames:
 *
 * <pre>{@code
 * ProgramCompile compile = ProgramCompile.start(vertex, fragment, List.of());
 * // each frame, until it answers true:
 * if (compile.ready()) {
 *     ProgramCompile.Linked linked = compile.finish();
 * }
 * }</pre>
 *
 * Render thread only.
 */
@Environment(EnvType.CLIENT)
public final class ProgramCompile {

    /** {@code GL_COMPLETION_STATUS_KHR}, also {@code _ARB}. */
    private static final int COMPLETION_STATUS = 0x91B1;
    private static final int INFO_LOG_LENGTH = 32768;
    /** Some drivers report a failed link this way with a success status; the game's device refuses it too. */
    private static final String UNKNOWN_LINK_FAILURE = "Failed for unknown reason";
    private static final long BLOCKING_SPACING_NS = 50_000_000L;

    private static Boolean parallel;
    private static long lastBlockingNs = Long.MIN_VALUE;

    private final String vertex;
    private final String fragment;
    private final List<String> attributes;
    private int vertexShader;
    private int fragmentShader;
    private int program;
    private boolean ended;

    private ProgramCompile(String vertex, String fragment, List<String> attributes) {
        this.vertex = vertex;
        this.fragment = fragment;
        this.attributes = List.copyOf(attributes);
    }

    /**
     * Starts compiling {@code vertex} and {@code fragment}, handed to the driver now where it compiles in the
     * background, else at the first {@link #ready()} its turn allows.
     *
     * @param attributes the vertex attributes bound to locations 0, 1, … in this order before the link; empty to
     *                   leave them to the driver
     */
    public static ProgramCompile start(String vertex, String fragment, List<String> attributes) {
        RenderSystem.assertOnRenderThread();
        ProgramCompile compile = new ProgramCompile(vertex, fragment, attributes);
        if (parallel()) compile.submit();
        return compile;
    }

    /** Whether {@link #finish()} returns without waiting on the driver; never waits itself. */
    public boolean ready() {
        RenderSystem.assertOnRenderThread();
        if (ended) return true;
        if (program == 0) {
            long now = System.nanoTime();
            if (lastBlockingNs != Long.MIN_VALUE && now - lastBlockingNs < BLOCKING_SPACING_NS) return false;
            submit();
            lastBlockingNs = System.nanoTime();
            return true;
        }
        return !parallel() || GlStateManager.glGetProgrami(program, COMPLETION_STATUS) != 0;
    }

    /**
     * Waits for the driver if it is not done, then hands over the program, its stages detached and deleted, or the
     * driver's logs with nothing left allocated.
     */
    public Linked finish() {
        RenderSystem.assertOnRenderThread();
        if (ended) throw new IllegalStateException("Program compile already finished");
        if (program == 0) {
            submit();
            lastBlockingNs = System.nanoTime();
        }
        ended = true;

        List<String> logs = new ArrayList<>();
        stageLog(ShaderType.VERTEX, vertexShader, logs);
        stageLog(ShaderType.FRAGMENT, fragmentShader, logs);
        if (logs.isEmpty()) {
            String info = GlStateManager.glGetProgramInfoLog(program, INFO_LOG_LENGTH);
            if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0 || info.contains(UNKNOWN_LINK_FAILURE))
                logs.add("link: " + StringUtils.trim(info));
        }
        if (!logs.isEmpty()) {
            free();
            return new Linked(0, String.join("\n", logs));
        }

        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GlStateManager.glDeleteShader(vertexShader);
        GlStateManager.glDeleteShader(fragmentShader);
        Linked linked = new Linked(program, null);
        vertexShader = fragmentShader = program = 0;
        return linked;
    }

    /** Deletes what the compile made so far. */
    public void cancel() {
        RenderSystem.assertOnRenderThread();
        if (ended) return;
        ended = true;
        free();
    }

    private void submit() {
        vertexShader = compileStage(ShaderType.VERTEX, vertex);
        fragmentShader = compileStage(ShaderType.FRAGMENT, fragment);
        program = GlStateManager.glCreateProgram();
        int location = 0;
        for (String name : attributes)
            GlStateManager._glBindAttribLocation(program, location++, name);
        GlStateManager.glAttachShader(program, vertexShader);
        GlStateManager.glAttachShader(program, fragmentShader);
        GlStateManager.glLinkProgram(program);
    }

    private void free() {
        if (program != 0) GlStateManager.glDeleteProgram(program);
        if (vertexShader != 0) GlStateManager.glDeleteShader(vertexShader);
        if (fragmentShader != 0) GlStateManager.glDeleteShader(fragmentShader);
        vertexShader = fragmentShader = program = 0;
    }

    private static int compileStage(ShaderType type, String source) {
        int shader = GlStateManager.glCreateShader(GlConst.toGl(type));
        GlStateManager.glShaderSource(shader, source);
        GlStateManager.glCompileShader(shader);
        return shader;
    }

    private static void stageLog(ShaderType type, int shader, List<String> logs) {
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            logs.add(type.getName() + ": " + StringUtils.trim(GlStateManager.glGetShaderInfoLog(shader, INFO_LOG_LENGTH)));
    }

    /** Whether the driver compiles and links in the background; its default thread count is already its maximum. */
    private static boolean parallel() {
        if (parallel == null) {
            GLCapabilities capabilities = GL.getCapabilities();
            parallel = capabilities.GL_KHR_parallel_shader_compile || capabilities.GL_ARB_parallel_shader_compile;
        }
        return parallel;
    }

    /**
     * What a compile gave.
     *
     * @param program  the linked program, which the caller now owns; 0 when it failed
     * @param problems the driver's logs when it failed, else {@code null}
     */
    public record Linked(int program, @Nullable String problems) {
    }
}
