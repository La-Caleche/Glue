package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.GluePostEffectRenderer;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.TimedPostEffect;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

/**
 * The showcase's post-effect owner: one {@link GluePostEffectRenderer} hosting the three
 * Java-built {@link TimedPostEffect}s alongside the data-driven effects triggered by id and
 * the toggled steady-state handles. Demos go through {@link #INSTANCE} so every effect shares
 * that single renderer; {@code TestmodClient} calls {@link #register()} once at client init.
 */
@Environment(EnvType.CLIENT)
public class TestPostShaderHandler {

    public static final TestPostShaderHandler INSTANCE = new TestPostShaderHandler();

    // Effects built in Java rather than loaded from JSON. CHROMATIC is the direct-path twin of the
    // registry-driven glue-test:chromatic definition — same chain, duration and reverse envelope —
    // so the showcase commands trigger one effect through both registration paths; SHATTERED and IMPACT
    // need per-frame uniform writers, which JSON cannot express.
    public static final TimedPostEffect CHROMATIC = TimedPostEffect.builder(TestShaders.CHROMATIC_ABERRATION)
            .ubo("ChromaticConfig", 4)
            .duration(15)
            .curveReverse()
            .build();

    private static final int SHATTERED_DURATION = 59;
    private static final float SHATTERED_START_FADE = 19f;
    private static final float SHATTERED_FADE_LENGTH = 40f;
    private static final float SHATTERED_FLASH_DURATION = 4f;

    public static final TimedPostEffect SHATTERED = TimedPostEffect.builder(TestShaders.SHATTERED_SCREEN)
            .ubo("ShatteredConfig", 16)
            .duration(SHATTERED_DURATION)
            .curve(t -> t)
            .uniforms(w -> {
                float t = w.progress() * SHATTERED_DURATION;
                float intensity = computeShatterIntensity(t);
                float flash = computeFlashIntensity(t);
                w.putFloat(intensity).putFloat(0.05f).putFloat(0.8f).putFloat(flash);
            })
            .build();

    private static final int IMPACT_DURATION = 10;

    public static final TimedPostEffect IMPACT = TimedPostEffect.builder(TestShaders.IMPACT_FRAME)
            .ubo("ImpactConfig", 16)
            .duration(IMPACT_DURATION)
            .uniforms(w -> w.putFloat(0.6f).putFloat(0.05f).putFloat(0.0f).putFloat(5.0f))
            .build();

    private final GluePostEffectRenderer renderer = new GluePostEffectRenderer();

    private TestPostShaderHandler() {
    }

    /**
     * Triggers a data-driven timed effect by id. The renderer bakes it once on
     * first use, reuses the instance afterwards, and drops the cache on resource
     * reload — see {@link GluePostEffectRenderer#triggerTimed(ResourceLocation)}.
     */
    public void triggerFromRegistry(ResourceLocation id) {
        renderer.triggerTimed(id);
    }

    /** Whether the data-driven effect for {@code id} is currently playing. */
    public boolean isRegistryActive(ResourceLocation id) {
        return renderer.isTimedActive(id);
    }

    public void register() {
        renderer
                .addTimed(SHATTERED)
                .addTimed(CHROMATIC)
                .addTimed(IMPACT)
                .register();
    }

    public boolean isToggled(PostShaderHandle handle) {
        return renderer.isToggled(handle);
    }

    public boolean toggleByHandle(PostShaderHandle handle) {
        return renderer.toggle(handle);
    }

    private static float computeShatterIntensity(float t) {
        if (t < SHATTERED_START_FADE) {
            return Math.min(t / 2.0f, 1.0f);
        }
        float fade = (t - SHATTERED_START_FADE) / SHATTERED_FADE_LENGTH;
        float inv = 1.0f - Math.min(fade, 1.0f);
        return inv * inv;
    }

    private static float computeFlashIntensity(float t) {
        if (t >= SHATTERED_FLASH_DURATION) return 0.0f;
        float inv = 1.0f - (t / SHATTERED_FLASH_DURATION);
        return inv * inv * inv;
    }
}
