package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.client.shader.PostShaderHandle;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.pipeline.RenderTarget;

import java.util.ArrayList;
import java.util.List;

public class TestPostShaderHandler {

    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    private static final List<PostShaderHandle> toggleEffects = new ArrayList<>();
    private static final List<TimedEffect> timedEffects = new ArrayList<>();

    private static final int SHATTERED_DURATION = 59;
    private static final float SHATTERED_START_FADE = 19f;
    private static final float SHATTERED_FADE_LENGTH = 40f;
    private static final float SHATTERED_FLASH_DURATION = 4f;

    public static final TimedEffect SHATTERED = TimedEffect.builder(TestShaders.SHATTERED_SCREEN)
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

    public static final TimedEffect DEPARTURE = TimedEffect.builder(TestShaders.DEPARTURE_VORTEX)
            .ubo("VortexConfig", 4)
            .duration(30)
            .curveLinear()
            .build();

    public static final TimedEffect ARRIVAL = TimedEffect.builder(TestShaders.ARRIVAL_SHOCKWAVE)
            .ubo("ShockwaveConfig", 4)
            .duration(20)
            .curveReverse()
            .build();

    public static final TimedEffect DENIAL = TimedEffect.builder(TestShaders.END_LOCKED_PULSE)
            .ubo("PulseConfig", 4)
            .duration(8)
            .curveReverse()
            .build();

    public static final TimedEffect SUN = TimedEffect.builder(TestShaders.SUN_SURFACE)
            .ubo("SunConfig", 4)
            .duration(200)
            .curveReverse()
            .build();

    public static final TimedEffect CHROMATIC = TimedEffect.builder(TestShaders.CHROMATIC_ABERRATION)
            .ubo("ChromaticConfig", 4)
            .duration(15)
            .curve(t -> (1.0f - t) * 0.05f)
            .build();

    private static final int IMPACT_DURATION = 10;
    private static final float IMPACT_INTENSITY = 0.6f;
    private static final float IMPACT_MAX_OFFSET = 0.05f;
    private static final float IMPACT_CHROMATIC = 0.0f;
    private static final float IMPACT_FLASH = 5.0f;

    public static final TimedEffect IMPACT = TimedEffect.builder(TestShaders.IMPACT_FRAME)
            .ubo("ImpactConfig", 16)
            .duration(IMPACT_DURATION)
            .uniforms(w -> w.putFloat(IMPACT_INTENSITY).putFloat(IMPACT_MAX_OFFSET).putFloat(IMPACT_CHROMATIC).putFloat(IMPACT_FLASH))
            .build();

    static {
        timedEffects.add(SHATTERED);
        timedEffects.add(DEPARTURE);
        timedEffects.add(ARRIVAL);
        timedEffects.add(DENIAL);
        timedEffects.add(SUN);
        timedEffects.add(CHROMATIC);
        timedEffects.add(IMPACT);
    }

    public static void register() {
        RenderEvents.POST_WORLD_RENDER.register(TestPostShaderHandler::onPostWorldRender);
        ClientTickEvents.END_CLIENT_TICK.register(TestPostShaderHandler::onClientTick);
    }

    public static boolean toggleEffect(PostShaderHandle handle) {
        if (toggleEffects.contains(handle)) {
            toggleEffects.remove(handle);
            return false;
        } else {
            toggleEffects.add(handle);
            return true;
        }
    }

    public static boolean isToggled(PostShaderHandle handle) {
        return toggleEffects.contains(handle);
    }

    public static boolean toggleBlur() {
        return toggleEffect(TestShaders.BLUR);
    }

    public static boolean toggleGrayscale() {
        return toggleEffect(TestShaders.GRAYSCALE);
    }

    private static void onClientTick(Minecraft client) {
        if (client.isPaused()) return;
        for (TimedEffect effect : timedEffects) {
            effect.tick();
        }
    }

    private static void onPostWorldRender() {
        if (RenderCompat.isRenderingShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RenderTarget target = mc.getMainRenderTarget();
        boolean anyApplied = false;

        for (PostShaderHandle toggle : toggleEffects) {
            toggle.apply(target, RESOURCE_POOL);
            anyApplied = true;
        }

        for (TimedEffect effect : timedEffects) {
            if (effect.render(mc, RESOURCE_POOL, partialTick)) {
                anyApplied = true;
            }
        }

        if (anyApplied) {
            RESOURCE_POOL.endFrame();
        }
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
