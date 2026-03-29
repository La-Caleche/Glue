package fr.lacaleche.glue.testmod.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
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
 * When the player is within 8 blocks of a TestShaderBlock:
 * <ul>
 *   <li>Applies the grayscale post-processing effect</li>
 *   <li>Applies a blur post-processing effect</li>
 * </ul>
 */
public class TestPostShaderHandler {

    private static final double EFFECT_RANGE = 8.0;
    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    /**
     * Register the post-shader render event handler.
     */
    public static void register() {
        WorldRenderEvents.LAST.register(TestPostShaderHandler::onWorldRenderLast);
    }

    private static void onWorldRenderLast(WorldRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        // Check if player is near a shader block
//        boolean nearShaderBlock = isNearShaderBlock(mc.level, player);
//
//        if (nearShaderBlock) {
//            // Apply grayscale post-processing
//            TestShaders.GRAYSCALE.apply(mc.getMainRenderTarget(), RESOURCE_POOL);
//        }
//
//        RESOURCE_POOL.endFrame();
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
