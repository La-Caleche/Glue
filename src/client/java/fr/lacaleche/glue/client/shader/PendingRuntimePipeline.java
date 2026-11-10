package fr.lacaleche.glue.client.shader;

import fr.lacaleche.glue.client.shader.internal.RuntimeCompile;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A runtime pipeline {@link GluePipeline.Builder#buildRuntimeAsync} is compiling. Where the driver compiles in the
 * background, {@link #poll()} never waits on it; elsewhere the pipelines started together compile one at a time,
 * spread over frames. Render thread only.
 */
@Environment(EnvType.CLIENT)
public final class PendingRuntimePipeline {

    private final RuntimeCompile compile;
    private final Function<String, RuntimePipelineResult> complete;
    private RuntimePipelineResult result;

    PendingRuntimePipeline(RuntimeCompile compile, Function<String, RuntimePipelineResult> complete) {
        this.compile = compile;
        this.complete = complete;
    }

    /**
     * What the compile made once it is done, the same result on every later call: a pipeline the caller then
     * {@linkplain GluePipeline#release() releases}, or the driver's logs. {@code null} while the driver works.
     */
    public @Nullable RuntimePipelineResult poll() {
        if (result == null && compile.ready()) result = complete.apply(compile.finish());
        return result;
    }

    /** Stops a compile not done yet and frees what it made; a result already polled stays the caller's. */
    public void cancel() {
        if (result == null) compile.cancel();
    }
}
