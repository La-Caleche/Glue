package fr.lacaleche.glue.client.shader;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * What {@link GluePipeline.Builder#buildRuntime} produced: a pipeline ready to draw, or the driver's
 * compile and link logs when the sources did not make one.
 */
@Environment(EnvType.CLIENT)
public sealed interface RuntimePipelineResult {

    record Compiled(GluePipeline pipeline) implements RuntimePipelineResult {
    }

    /**
     * @param log the info logs of the stages that failed, or of the link, each headed by what it is
     */
    record Failed(String log) implements RuntimePipelineResult {
    }
}
