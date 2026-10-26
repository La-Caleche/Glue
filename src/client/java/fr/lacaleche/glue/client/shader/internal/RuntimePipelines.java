package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.opengl.GlConst;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.pipeline.CompiledRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.mixin.shader.runtime.GlDeviceAccessor;
import fr.lacaleche.glue.client.mixin.shader.runtime.ShaderCompilationKeyAccessor;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.ShaderSources;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL20;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Compiles, releases and recompiles the pipelines built from {@link ShaderSources}. Their shader id is
 * unique per build, so the device's shader cache never hands one pipeline another's modules, and releasing
 * one closes only what it owns. Render thread only.
 */
@Environment(EnvType.CLIENT)
public final class RuntimePipelines {

    private static final Logger LOGGER = LoggerFactory.getLogger("glue/runtime-shader");
    private static final int INFO_LOG_LENGTH = 32768;
    /** Some drivers report a failed link this way with a success status; the device refuses it too. */
    private static final String UNKNOWN_LINK_FAILURE = "Failed for unknown reason";

    private static final Map<GluePipeline, ShaderSources> LIVE = new WeakHashMap<>();
    private static int serial;
    private static boolean shutdown;

    private RuntimePipelines() {
    }

    /** Both stages share the id; the device keys its modules by id and stage. */
    public static ResourceLocation nextShaderId(ResourceLocation location) {
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(),
                "runtime/" + location.getPath() + "/" + serial++);
    }

    /**
     * Compiles and links the sources once by hand first, so a failure leaves no shader or program behind:
     * the device would keep a failed stage in its cache and leak a program that fails to link.
     *
     * @return null once the pipeline is in the device's cache, else the driver's logs, with nothing allocated
     */
    public static @Nullable String compile(RenderPipeline pipeline, ShaderSources sources) {
        RenderSystem.assertOnRenderThread();
        String vertex = RuntimeShaderPreprocessor.expand(sources.vertex(), sources.includes(), RuntimePipelines::readResource);
        String fragment = RuntimeShaderPreprocessor.expand(sources.fragment(), sources.includes(), RuntimePipelines::readResource);
        String log = check(pipeline, vertex, fragment);
        if (log != null) return log;

        ResourceLocation id = pipeline.getVertexShader();
        CompiledRenderPipeline compiled = RenderSystem.getDevice().precompilePipeline(pipeline, (shaderId, type) ->
                shaderId.equals(id) ? (type == ShaderType.VERTEX ? vertex : fragment) : null);
        if (compiled.isValid()) return null;

        evict(pipeline);
        return "the program links here; the device refused it anyway, see its log";
    }

    public static void register(GluePipeline pipeline, ShaderSources sources) {
        LIVE.put(pipeline, sources);
    }

    /** Drops the pipeline from the device's caches and deletes its program and shaders. */
    public static void release(GluePipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        LIVE.remove(pipeline);
        evict(pipeline.getPipeline());
    }

    /** From the tail of {@code GlDevice.clearPipelineCache}: a resource reload, or anyone else clearing it. */
    public static void recompileAll() {
        if (shutdown || LIVE.isEmpty()) return;
        for (Map.Entry<GluePipeline, ShaderSources> entry : new ArrayList<>(LIVE.entrySet())) {
            String log = compile(entry.getKey().getPipeline(), entry.getValue());
            if (log != null)
                LOGGER.error("[Glue] Runtime pipeline '{}' no longer compiles after the cache was cleared:\n{}",
                        entry.getKey().getName(), log);
        }
    }

    public static void shutdown() {
        shutdown = true;
    }

    private static void evict(RenderPipeline pipeline) {
        GlDeviceAccessor device = (GlDeviceAccessor) RenderSystem.getDevice();
        GlRenderPipeline compiled = device.glue$getPipelineCache().remove(pipeline);
        if (compiled != null && compiled.program() != GlProgram.INVALID_PROGRAM)
            compiled.program().close();

        ResourceLocation id = pipeline.getVertexShader();
        Iterator<? extends Map.Entry<?, GlShaderModule>> modules = device.glue$getShaderCache().entrySet().iterator();
        while (modules.hasNext()) {
            Map.Entry<?, GlShaderModule> entry = modules.next();
            if (!((ShaderCompilationKeyAccessor) entry.getKey()).glue$getId().equals(id)) continue;
            if (entry.getValue() != GlShaderModule.INVALID_SHADER)
                entry.getValue().close();
            modules.remove();
        }
    }

    /** Compiles and links as the device does, attribute locations included, and deletes what it made. */
    private static @Nullable String check(RenderPipeline pipeline, String vertex, String fragment) {
        List<String> logs = new ArrayList<>();
        int vs = compileStage(ShaderType.VERTEX, GlslPreprocessor.injectDefines(vertex, pipeline.getShaderDefines()), logs);
        int fs = compileStage(ShaderType.FRAGMENT, GlslPreprocessor.injectDefines(fragment, pipeline.getShaderDefines()), logs);
        if (logs.isEmpty()) {
            int program = GlStateManager.glCreateProgram();
            int location = 0;
            for (String name : pipeline.getVertexFormat().getElementAttributeNames())
                GlStateManager._glBindAttribLocation(program, location++, name);
            GlStateManager.glAttachShader(program, vs);
            GlStateManager.glAttachShader(program, fs);
            GlStateManager.glLinkProgram(program);
            String info = GlStateManager.glGetProgramInfoLog(program, INFO_LOG_LENGTH);
            if (GlStateManager.glGetProgrami(program, GL20.GL_LINK_STATUS) == 0 || info.contains(UNKNOWN_LINK_FAILURE))
                logs.add("link: " + StringUtils.trim(info));
            GlStateManager.glDeleteProgram(program);
        }
        GlStateManager.glDeleteShader(vs);
        GlStateManager.glDeleteShader(fs);
        return logs.isEmpty() ? null : String.join("\n", logs);
    }

    private static int compileStage(ShaderType type, String source, List<String> logs) {
        int shader = GlStateManager.glCreateShader(GlConst.toGl(type));
        GlStateManager.glShaderSource(shader, source);
        GlStateManager.glCompileShader(shader);
        if (GlStateManager.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0)
            logs.add(type.getName() + ": " + StringUtils.trim(GlStateManager.glGetShaderInfoLog(shader, INFO_LOG_LENGTH)));
        return shader;
    }

    private static @Nullable String readResource(ResourceLocation location) {
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(location);
        if (resource.isEmpty()) return null;
        try (Reader reader = resource.get().openAsReader()) {
            return IOUtils.toString(reader);
        } catch (IOException e) {
            return "#error Couldn't read " + location + ": " + e.getMessage() + "\n";
        }
    }
}
