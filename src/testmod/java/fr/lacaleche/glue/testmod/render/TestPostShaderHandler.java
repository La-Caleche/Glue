package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlock;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Handles post-processing shader application based on player proximity to shader blocks.
 * <p>
 * Tests:
 * <ul>
 *   <li>Grayscale (always active near shader block)</li>
 *   <li>Blur (toggle via B key)</li>
 *   <li>Shattered screen (one-shot via V key — flash → shatter → fade out)</li>
 * </ul>
 */
public class TestPostShaderHandler {

    private static final double EFFECT_RANGE = 8.0;
    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    private static boolean blurEnabled = false;

    // --- Shattered screen one-shot effect ---
    //
    // Lifecycle (in ticks):
    //   Phase 1: FLASH   — bright white impact flash that decays quickly
    //   Phase 2: HOLD    — full shatter distortion at max strength
    //   Phase 3: FADEOUT — shatter distortion smoothly fades to zero
    //
    private static final int FLASH_TICKS    = 4;   // ~200ms — sharp white flash
    private static final int HOLD_TICKS     = 15;  // ~750ms — full shatter
    private static final int FADEOUT_TICKS  = 40;  // ~2s    — smooth fade out
    private static final int TOTAL_TICKS    = FLASH_TICKS + HOLD_TICKS + FADEOUT_TICKS;

    // Shatter parameters
    private static final float MAX_OFFSET = 0.05f;
    private static final float CHROMATIC_STRENGTH = 0.8f;

    /** Std140 buffer size for ShatteredConfig: 4 floats × 4 bytes = 16 bytes */
    private static final int SHATTERED_CONFIG_SIZE = 16;

    private static int shatteredTick = -1; // -1 = inactive

    /**
     * Register event handlers.
     */
    public static void register() {
        WorldRenderEvents.LAST.register(TestPostShaderHandler::onWorldRenderLast);
        ClientTickEvents.END_CLIENT_TICK.register(TestPostShaderHandler::onClientTick);
    }

    /**
     * Toggles the blur post-processing effect on/off.
     */
    public static boolean toggleBlur() {
        blurEnabled = !blurEnabled;
        return blurEnabled;
    }

    public static boolean isBlurEnabled() {
        return blurEnabled;
    }

    /**
     * Triggers the shattered screen effect (one-shot).
     * If already playing, restarts from the beginning.
     */
    public static void triggerShattered() {
        shatteredTick = 0;
    }

    /**
     * @return Whether the shattered screen effect is currently playing
     */
    public static boolean isShatteredActive() {
        return shatteredTick >= 0 && shatteredTick < TOTAL_TICKS;
    }

    /**
     * Tick the shattered screen effect lifecycle.
     */
    private static void onClientTick(Minecraft client) {
        if (client.isPaused()) return;

        if (shatteredTick >= 0) {
            shatteredTick++;
            if (shatteredTick >= TOTAL_TICKS) {
                shatteredTick = -1; // Effect ended
            }
        }
    }

    /**
     * Computes the shatter distortion intensity (0.0 – 1.0) at the current time.
     * <ul>
     *   <li>Flash phase: ramps to 1.0 instantly</li>
     *   <li>Hold phase: stays at 1.0</li>
     *   <li>Fade-out phase: smooth ease-out curve from 1.0 to 0.0</li>
     * </ul>
     */
    private static float getShatterIntensity(float partialTick) {
        if (shatteredTick < 0) return 0.0f;

        float t = Mth.clamp(shatteredTick + partialTick, 0, TOTAL_TICKS);

        if (t < FLASH_TICKS + HOLD_TICKS) {
            // During flash + hold: full distortion
            // Ramp in quickly during the first couple ticks
            float rampEnd = 2.0f; // 2 ticks to reach full strength
            return Mth.clamp(t / rampEnd, 0.0f, 1.0f);
        } else {
            // Fade-out phase: smooth ease-out (quadratic)
            float fadeProgress = (t - FLASH_TICKS - HOLD_TICKS) / FADEOUT_TICKS;
            float inv = 1.0f - Mth.clamp(fadeProgress, 0.0f, 1.0f);
            return inv * inv; // Quadratic ease-out
        }
    }

    /**
     * Computes the flash overlay intensity (0.0 – 1.0) at the current time.
     * Sharp spike at the start that decays exponentially.
     */
    private static float getFlashIntensity(float partialTick) {
        if (shatteredTick < 0) return 0.0f;

        float t = Mth.clamp(shatteredTick + partialTick, 0, TOTAL_TICKS);

        if (t >= FLASH_TICKS) return 0.0f;

        // Exponential decay from 1.0 to 0.0 over FLASH_TICKS
        float progress = t / FLASH_TICKS;
        float inv = 1.0f - progress;
        return inv * inv * inv; // Cubic decay — punchy start, fast falloff
    }

    private static void onWorldRenderLast(WorldRenderContext context) {
        if (RenderCompat.isRenderingShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        boolean nearShaderBlock = isNearShaderBlock(mc.level, player);

        if (nearShaderBlock) {
            // Apply blur post-processing (toggle via keybind)
            if (blurEnabled) {
                TestShaders.BLUR.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
            }
        }

        // Shattered screen: one-shot, plays anywhere (not proximity-gated)
        if (isShatteredActive()) {
            float partialTick = context.tickCounter().getGameTimeDeltaPartialTick(false);
            float intensity = getShatterIntensity(partialTick);
            float flash = getFlashIntensity(partialTick);

            // Update the ShatteredConfig UBO with current frame values
            TestShaders.SHATTERED_SCREEN.setUniform("ShatteredConfig", SHATTERED_CONFIG_SIZE, builder -> {
                builder.putFloat(intensity);               // Intensity
                builder.putFloat(MAX_OFFSET);              // MaxOffset
                builder.putFloat(CHROMATIC_STRENGTH);      // ChromaticStrength
                builder.putFloat(flash);                   // FlashIntensity
            });

            TestShaders.SHATTERED_SCREEN.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
        }

        RESOURCE_POOL.endFrame();
    }

    private static boolean isNearShaderBlock(Level level, Player player) {
        BlockPos playerPos = player.blockPosition();
        int range = (int) EFFECT_RANGE;

        for (BlockPos pos : BlockPos.betweenClosed(
                playerPos.offset(-range, -range, -range),
                playerPos.offset(range, range, range))) {
            if (level.getBlockState(pos).getBlock() instanceof TestShaderBlock) {
                double dist = player.position().distanceTo(pos.getCenter());
                if (dist <= EFFECT_RANGE) {
                    return true;
                }
            }
        }
        return false;
    }
}
