package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.client.events.RenderEvents;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Applies post-processing shaders: grayscale (proximity), blur (toggle), shattered screen (one-shot).
 */
public class TestPostShaderHandler {

    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);
    private static final int SHATTERED_CONFIG_SIZE = 16; // 4 floats × 4 bytes

    private static final int FLASH_TICKS = 4;
    private static final int HOLD_TICKS = 15;
    private static final int FADEOUT_TICKS = 40;
    private static final int TOTAL_TICKS = FLASH_TICKS + HOLD_TICKS + FADEOUT_TICKS;

    private static final float MAX_OFFSET = 0.05f;
    private static final float CHROMATIC_STRENGTH = 0.8f;

    private static boolean grayscaleEnabled = false;
    private static boolean blurEnabled = false;
    private static int shatteredTick = -1;

    public static void register() {
        // Use POST_WORLD_RENDER so effects apply AFTER Glue blit (includes custom shaders)
        RenderEvents.POST_WORLD_RENDER.register(TestPostShaderHandler::onPostWorldRender);
        ClientTickEvents.END_CLIENT_TICK.register(TestPostShaderHandler::onClientTick);
    }

    public static boolean toggleBlur() {
        blurEnabled = !blurEnabled;
        return blurEnabled;
    }

    public static boolean toggleGrayscale() {
        grayscaleEnabled = !grayscaleEnabled;
        return grayscaleEnabled;
    }

    public static boolean isBlurEnabled() {
        return blurEnabled;
    }

    public static void triggerShattered() {
        shatteredTick = 0;
    }

    public static boolean isShatteredActive() {
        return shatteredTick >= 0 && shatteredTick < TOTAL_TICKS;
    }

    private static void onClientTick(Minecraft client) {
        if (client.isPaused()) return;

        if (shatteredTick >= 0) {
            shatteredTick++;
            if (shatteredTick >= TOTAL_TICKS) {
                shatteredTick = -1;
            }
        }
    }

    private static void onPostWorldRender() {
        if (RenderCompat.isRenderingShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean anyApplied = false;

        if (grayscaleEnabled) {
            TestShaders.GRAYSCALE.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
            anyApplied = true;
        }

        if (blurEnabled) {
            TestShaders.BLUR.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
            anyApplied = true;
        }

        if (isShatteredActive()) {
            float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            applyShatteredScreen(mc, partialTick);
            anyApplied = true;
        }

        if (anyApplied) {
            RESOURCE_POOL.endFrame();
        }
    }

    private static void applyShatteredScreen(Minecraft mc, float partialTick) {
        float intensity = getShatterIntensity(partialTick);
        float flash = getFlashIntensity(partialTick);

        TestShaders.SHATTERED_SCREEN.setUniform("ShatteredConfig", SHATTERED_CONFIG_SIZE, builder -> {
            builder.putFloat(intensity);
            builder.putFloat(MAX_OFFSET);
            builder.putFloat(CHROMATIC_STRENGTH);
            builder.putFloat(flash);
        });

        TestShaders.SHATTERED_SCREEN.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
    }

    private static float getShatterIntensity(float partialTick) {
        if (shatteredTick < 0) return 0.0f;
        float t = Mth.clamp(shatteredTick + partialTick, 0, TOTAL_TICKS);

        if (t < FLASH_TICKS + HOLD_TICKS) {
            return Mth.clamp(t / 2.0f, 0.0f, 1.0f);
        }

        float fadeProgress = (t - FLASH_TICKS - HOLD_TICKS) / FADEOUT_TICKS;
        float inv = 1.0f - Mth.clamp(fadeProgress, 0.0f, 1.0f);
        return inv * inv;
    }

    private static float getFlashIntensity(float partialTick) {
        if (shatteredTick < 0) return 0.0f;
        float t = Mth.clamp(shatteredTick + partialTick, 0, TOTAL_TICKS);
        if (t >= FLASH_TICKS) return 0.0f;

        float inv = 1.0f - (t / FLASH_TICKS);
        return inv * inv * inv;
    }
}
