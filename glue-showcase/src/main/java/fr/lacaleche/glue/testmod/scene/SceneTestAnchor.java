package fr.lacaleche.glue.testmod.scene;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

/** A stable terrain anchor, including when the player opens a scene while flying. */
final class SceneTestAnchor {

    private static final int NEARBY_SEARCH_DEPTH = 32;

    private SceneTestAnchor() {
    }

    static BlockPos aroundPlayer(Minecraft client) {
        if (client.player == null || client.level == null) return BlockPos.ZERO;

        BlockPos playerFloor = client.player.getOnPos();
        for (int depth = 0; depth <= NEARBY_SEARCH_DEPTH; depth++) {
            BlockPos candidate = playerFloor.below(depth);
            if (!client.level.getBlockState(candidate).isAir()) return candidate;
        }

        int surfaceY = client.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                playerFloor.getX(), playerFloor.getZ()) - 1;
        return new BlockPos(playerFloor.getX(), surfaceY, playerFloor.getZ());
    }
}
