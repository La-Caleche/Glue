package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import fr.lacaleche.glue.compat.RenderCompat;
import fr.lacaleche.glue.testmod.blocks.demo.TestShaderBlock;
import fr.lacaleche.glue.testmod.registries.TestShaders;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Handles post-processing shader application based on player proximity to shader blocks.
 * <p>
 * When the player is within range of a TestShaderBlock:
 * <ul>
 *   <li>Applies the grayscale post-processing effect (always when near block)</li>
 *   <li>Applies the blur post-processing effect (toggleable via keybind)</li>
 * </ul>
 * <p>
 * Tests both {@code TestShaders.GRAYSCALE} and {@code TestShaders.BLUR}.
 */
public class TestPostShaderHandler {

    private static final double EFFECT_RANGE = 8.0;
    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    private static boolean blurEnabled = false;

    /**
     * Register the post-shader render event handler.
     */
    public static void register() {
        WorldRenderEvents.LAST.register(TestPostShaderHandler::onWorldRenderLast);
    }

    /**
     * Toggles the blur post-processing effect on/off.
     *
     * @return The new state of the blur effect
     */
    public static boolean toggleBlur() {
        blurEnabled = !blurEnabled;
        return blurEnabled;
    }

    /**
     * @return Whether the blur post-processing effect is currently enabled
     */
    public static boolean isBlurEnabled() {
        return blurEnabled;
    }

    private static void onWorldRenderLast(WorldRenderContext context) {
        // Skip during Iris shadow pass — applying post-processing in the shadow pass
        // would corrupt shadow map rendering and cause visual artifacts
        if (RenderCompat.isRenderingShadowPass()) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        boolean nearShaderBlock = isNearShaderBlock(mc.level, player);

        if (nearShaderBlock) {
            // Apply grayscale post-processing (always active near shader block)
            TestShaders.GRAYSCALE.apply(mc.getMainRenderTarget(), RESOURCE_POOL);

            // Apply blur post-processing (toggle via keybind)
            if (blurEnabled) {
                TestShaders.BLUR.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
            }
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
