package fr.lacaleche.glue.client.registries;

import fr.lacaleche.glue.client.render.outline.GlueOutlineRenderer;
import fr.lacaleche.glue.client.shader.GluePipeline;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinition;

/**
 * Central hub for all Glue client-side {@link ReloadableRegistry} instances.
 */
public class GlueClientRegistries {

    public static final ReloadableRegistry<GlueOutlineRenderer> OUTLINE_RENDERERS = new ReloadableRegistry<>("outline_renderer");

    public static final ReloadableRegistry<GluePipeline> PIPELINES = new ReloadableRegistry<>("pipeline");

    public static final ReloadableRegistry<PostShaderHandle> POST_CHAINS = new ReloadableRegistry<>("post_chain");

    /** Stores definitions, not baked instances. Call {@link TimedEffectDefinition#bake()} to get a stateful instance. */
    public static final ReloadableRegistry<TimedEffectDefinition> TIMED_EFFECT_DEFINITIONS = new ReloadableRegistry<>("timed_effect_definition");
}
