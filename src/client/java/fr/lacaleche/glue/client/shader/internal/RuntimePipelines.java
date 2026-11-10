package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.opengl.GlShaderModule;
import com.mojang.blaze3d.pipeline.RenderPipeline;
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
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
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
     * Starts compiling the sources into {@code pipeline}, in the background where the driver can; see
     * {@link RuntimeCompile}. Nothing reaches the device's cache before {@link RuntimeCompile#finish()}.
     */
    public static RuntimeCompile start(RenderPipeline pipeline, ShaderSources sources) {
        RenderSystem.assertOnRenderThread();
        String vertex = RuntimeShaderPreprocessor.expand(sources.vertex(), sources.includes(), RuntimePipelines::readResource);
        String fragment = RuntimeShaderPreprocessor.expand(sources.fragment(), sources.includes(), RuntimePipelines::readResource);
        return RuntimeCompile.start(pipeline, vertex, fragment);
    }

    /**
     * Compiles and links the sources now, once: a failure leaves no shader or program behind.
     *
     * @return null once the pipeline is in the device's cache, else the driver's logs, with nothing allocated
     */
    public static @Nullable String compile(RenderPipeline pipeline, ShaderSources sources) {
        return start(pipeline, sources).finish();
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
