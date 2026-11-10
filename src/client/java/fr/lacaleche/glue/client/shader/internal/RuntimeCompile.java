package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.mixin.shader.runtime.GlDeviceAccessor;
import fr.lacaleche.glue.client.mixin.shader.runtime.GlProgramInvoker;
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
 * One compile and link of a runtime pipeline's two stages, done as the device does, attribute locations
 * included, and put in the device's pipeline cache once it links. Where the driver compiles in the
 * background ({@code GL_KHR_parallel_shader_compile} or {@code GL_ARB_parallel_shader_compile}), the work is
 * handed to it at once and {@link #ready()} asks, without waiting, whether it is done. Elsewhere it is done on
 * the render thread, at most one every {@link #BLOCKING_SPACING_NS}, so that compiles started together spread
 * over frames. Stages are detached and deleted once linked: only the program stays. Render thread only.
 */
@Environment(EnvType.CLIENT)
public final class RuntimeCompile {

    /** {@code GL_COMPLETION_STATUS_KHR}, also {@code _ARB}. */
    private static final int COMPLETION_STATUS = 0x91B1;
    private static final int INFO_LOG_LENGTH = 32768;
    /** Some drivers report a failed link this way with a success status; the device refuses it too. */
    private static final String UNKNOWN_LINK_FAILURE = "Failed for unknown reason";
    private static final long BLOCKING_SPACING_NS = 50_000_000L;

    private static Boolean parallel;
    private static long lastBlockingNs = Long.MIN_VALUE;

    private final RenderPipeline pipeline;
    private final String vertex;
    private final String fragment;
    private int vertexShader;
    private int fragmentShader;
    private int program;
    private boolean ended;

    private RuntimeCompile(RenderPipeline pipeline, String vertex, String fragment) {
        this.pipeline = pipeline;
        this.vertex = GlslPreprocessor.injectDefines(vertex, pipeline.getShaderDefines());
        this.fragment = GlslPreprocessor.injectDefines(fragment, pipeline.getShaderDefines());
    }

    /**
     * Starts compiling {@code pipeline} from the expanded {@code vertex} and {@code fragment} sources: handed to
     * the driver now where it compiles in the background, else at the first {@link #ready()} its turn allows.
     */
    public static RuntimeCompile start(RenderPipeline pipeline, String vertex, String fragment) {
        RenderSystem.assertOnRenderThread();
        RuntimeCompile compile = new RuntimeCompile(pipeline, vertex, fragment);
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
     * Waits for the driver if it is not done, then puts the linked program in the device's cache, in place of
     * any earlier one of the pipeline.
     *
     * @return null once the pipeline is in the cache, else the driver's logs, with nothing left allocated
     */
    public @Nullable String finish() {
        RenderSystem.assertOnRenderThread();
        if (ended) throw new IllegalStateException("Compile of " + pipeline.getLocation() + " already finished");
        if (program == 0) submit();
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
            return String.join("\n", logs);
        }

        GL20.glDetachShader(program, vertexShader);
        GL20.glDetachShader(program, fragmentShader);
        GlStateManager.glDeleteShader(vertexShader);
        GlStateManager.glDeleteShader(fragmentShader);
        GlProgram linked = GlProgramInvoker.glue$create(program, pipeline.getLocation().toString());
        vertexShader = fragmentShader = program = 0;
        linked.setupUniforms(pipeline.getUniforms(), pipeline.getSamplers());

        GlRenderPipeline previous = ((GlDeviceAccessor) RenderSystem.getDevice()).glue$getPipelineCache()
                .put(pipeline, new GlRenderPipeline(pipeline, linked));
        if (previous != null && previous.program() != GlProgram.INVALID_PROGRAM)
            previous.program().close();
        return null;
    }

    /** Deletes what the compile made so far; nothing reaches the device's cache. */
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
        for (String name : pipeline.getVertexFormat().getElementAttributeNames())
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
}
