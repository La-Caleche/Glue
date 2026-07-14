package fr.lacaleche.glue.client.render.internal.world;

import fr.lacaleche.glue.client.render.pipeline.WorldRenderPipelines;

/** Registers Glue's built-in world-frame providers. */
public final class DefaultWorldRenderPipelines {

    private DefaultWorldRenderPipelines() {
    }

    public static void register() {
        WorldRenderPipelines.register(new MainTargetWorldRenderPipeline(), 0);
        WorldRenderPipelines.register(new IrisWorldRenderPipeline(), 100);
    }
}
