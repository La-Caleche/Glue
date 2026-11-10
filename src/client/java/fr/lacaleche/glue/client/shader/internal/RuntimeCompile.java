package fr.lacaleche.glue.client.shader.internal;

import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.opengl.GlRenderPipeline;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.systems.RenderSystem;
import fr.lacaleche.glue.client.mixin.shader.runtime.GlDeviceAccessor;
import fr.lacaleche.glue.client.mixin.shader.runtime.GlProgramInvoker;
import fr.lacaleche.glue.client.render.gl.ProgramCompile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

/**
 * One compile and link of a runtime pipeline's two stages, done as the device does, attribute locations
 * included, and put in the device's pipeline cache once it links. The {@link ProgramCompile} underneath compiles
 * in the driver's background where it can, else spread over frames. Render thread only.
 */
@Environment(EnvType.CLIENT)
public final class RuntimeCompile {

    private final RenderPipeline pipeline;
    private final ProgramCompile compile;
    private boolean ended;

    private RuntimeCompile(RenderPipeline pipeline, ProgramCompile compile) {
        this.pipeline = pipeline;
        this.compile = compile;
    }

    /**
     * Starts compiling {@code pipeline} from the expanded {@code vertex} and {@code fragment} sources: handed to
     * the driver now where it compiles in the background, else at the first {@link #ready()} its turn allows.
     */
    public static RuntimeCompile start(RenderPipeline pipeline, String vertex, String fragment) {
        return new RuntimeCompile(pipeline, ProgramCompile.start(
                GlslPreprocessor.injectDefines(vertex, pipeline.getShaderDefines()),
                GlslPreprocessor.injectDefines(fragment, pipeline.getShaderDefines()),
                pipeline.getVertexFormat().getElementAttributeNames()));
    }

    /** Whether {@link #finish()} returns without waiting on the driver; never waits itself. */
    public boolean ready() {
        RenderSystem.assertOnRenderThread();
        return ended || compile.ready();
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
        ended = true;
        ProgramCompile.Linked linked = compile.finish();
        if (linked.problems() != null) return linked.problems();

        GlProgram program = GlProgramInvoker.glue$create(linked.program(), pipeline.getLocation().toString());
        program.setupUniforms(pipeline.getUniforms(), pipeline.getSamplers());
        GlRenderPipeline previous = ((GlDeviceAccessor) RenderSystem.getDevice()).glue$getPipelineCache()
                .put(pipeline, new GlRenderPipeline(pipeline, program));
        if (previous != null && previous.program() != GlProgram.INVALID_PROGRAM)
            previous.program().close();
        return null;
    }

    /** Deletes what the compile made so far; nothing reaches the device's cache. */
    public void cancel() {
        RenderSystem.assertOnRenderThread();
        if (ended) return;
        ended = true;
        compile.cancel();
    }
}
