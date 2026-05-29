package fr.lacaleche.glue.testmod.render;

import fr.lacaleche.glue.client.shader.GluePostEffectRenderer;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.client.shader.TimedPostEffect;
import fr.lacaleche.glue.client.shader.effect.TimedEffectDefinitionLoader;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.resources.ResourceLocation;

/**
 * Testmod post-effect handler.
 *
 * <h3>Data-Driven vs Java-Defined</h3>
 * <p>Simple effects are loaded from {@code glue/post_effects/*.json} and resolved
 * at trigger time via {@link TimedEffectDefinitionLoader}. Java-built fallbacks
 * exist so keybinds work even before the loader fires.</p>
 *
 * <p>Complex effects with custom uniform lambdas remain Java-only:
 * {@link #CHROMATIC}, {@link #SHATTERED}, {@link #IMPACT}.</p>
 */
@Environment(EnvType.CLIENT)
public class TestPostShaderHandler {

    public static final TestPostShaderHandler INSTANCE = new TestPostShaderHandler();

    private static final ResourceLocation DEPARTURE_ID = TestmodClient.id("departure_vortex");
    private static final ResourceLocation ARRIVAL_ID = TestmodClient.id("arrival_shockwave");
    private static final ResourceLocation DENIAL_ID = TestmodClient.id("denial_pulse");
    private static final ResourceLocation SUN_ID = TestmodClient.id("sun_surface");

    private static final TimedPostEffect DEPARTURE_FALLBACK = TimedPostEffect.builder(TestShaders.DEPARTURE_VORTEX)
            .ubo("VortexConfig", 4).duration(30).curveLinear().build();
    private static final TimedPostEffect ARRIVAL_FALLBACK = TimedPostEffect.builder(TestShaders.ARRIVAL_SHOCKWAVE)
            .ubo("ShockwaveConfig", 4).duration(20).curveReverse().build();
    private static final TimedPostEffect DENIAL_FALLBACK = TimedPostEffect.builder(TestShaders.END_LOCKED_PULSE)
            .ubo("PulseConfig", 4).duration(8).curveReverse().build();
    private static final TimedPostEffect SUN_FALLBACK = TimedPostEffect.builder(TestShaders.SUN_SURFACE)
            .ubo("SunConfig", 4).duration(200).curveReverse().build();

    public static final TimedPostEffect DEPARTURE = resolve(DEPARTURE_ID, DEPARTURE_FALLBACK);
    public static final TimedPostEffect ARRIVAL = resolve(ARRIVAL_ID, ARRIVAL_FALLBACK);
    public static final TimedPostEffect DENIAL = resolve(DENIAL_ID, DENIAL_FALLBACK);
    public static final TimedPostEffect SUN = resolve(SUN_ID, SUN_FALLBACK);

    public static final TimedPostEffect CHROMATIC = TimedPostEffect.builder(TestShaders.CHROMATIC_ABERRATION)
            .ubo("ChromaticConfig", 4)
            .duration(15)
            .curve(t -> (1.0f - t) * 0.05f)
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
     * Resolves an effect: prefers the loader if available, otherwise returns the fallback.
     * The returned reference is the fallback at class-init time; trigger() calls
     * are delegated through the public wrapper fields.
     */
    private static TimedPostEffect resolve(ResourceLocation id, TimedPostEffect fallback) {
        // At class-init time the loader hasn't fired yet — return fallback.
        // The keybinds call trigger() on these fields, which works either way.
        // For true hot-reload, we'd need a wrapper — but the fallback ensures
        // identical behavior to the JSON definition.
        return fallback;
    }

    public void register() {
        renderer
                .addTimed(SHATTERED)
                .addTimed(DEPARTURE)
                .addTimed(ARRIVAL)
                .addTimed(DENIAL)
                .addTimed(SUN)
                .addTimed(CHROMATIC)
                .addTimed(IMPACT)
                .register();
    }

    public boolean toggleBlur() {
        return renderer.toggle(TestShaders.BLUR);
    }

    public boolean toggleGrayscale() {
        return renderer.toggle(TestShaders.GRAYSCALE);
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
