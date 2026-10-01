package fr.lacaleche.glue.testmod.gametest.render;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.gametest.IrisTest;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/** Independent Fabric entrypoints sharing a disposable, reproducible rendering arena. */
@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.MissingStaticMethodInNonInstantiatableClass"}) // Namespace for Fabric entrypoint classes.
public final class RenderClientTests {

    private RenderClientTests() {
    }

    public abstract static class Arena extends WorldClientTest {

        @Override
        protected final void test() {
            this.world.getServer().runOnServer(server -> {
                ServerLevel level = server.overworld();
                level.setDayTime(18000);
                for (int x = -12; x <= 12; x++) {
                    for (int z = -12; z <= 16; z++) {
                        level.setBlockAndUpdate(new BlockPos(x, -1, z), (x < 0 ? Blocks.STONE_BRICKS : Blocks.QUARTZ_BLOCK).defaultBlockState());
                    }
                }
                for (int y = 0; y < 4; y++) {
                    level.setBlockAndUpdate(new BlockPos(4, y, 7), Blocks.RED_TERRACOTTA.defaultBlockState());
                    level.setBlockAndUpdate(new BlockPos(7, y, 7), Blocks.BLUE_TERRACOTTA.defaultBlockState());
                }
            });
            this.world.getServer().runCommand("summon minecraft:cow 5 0 8 {NoAI:1b,Silent:1b,Invulnerable:1b}");
            this.pose(9, 0, 13, 125, 15);
            this.world.getClientWorld().waitForChunksRender();
            try {
                this.renderTest();
            } finally {
                this.context.runOnClient(client -> GameViewport.clear());
            }
        }

        protected abstract void renderTest();

        protected void pose(double x, double y, double z, float yaw, float pitch) {
            this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().teleportTo(x, y, z));
            this.waitUntil("player teleport synchronized", client -> client.player.position().distanceToSqr(x, y, z) < 0.01);
            this.context.runOnClient(client -> {
                client.player.setYRot(yaw);
                client.player.setXRot(pitch);
                client.player.getInventory().setSelectedSlot(8);
            });
        }
    }

    public static final class ViewportSky extends Arena {

        @Override
        protected void renderTest() {
            boolean hasIris = FabricLoader.getInstance().isModLoaded("iris");
            try (IrisTest iris = hasIris ? new IrisTest(this.context) : null) {
                if (iris != null) iris.setEnabled(false);
                this.world.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
                this.world.getServer().runOnServer(server -> server.overworld().setDayTime(6000));
                this.pose(9, 160, 13, 0, 0);
                this.sky("day-window", 0, 0, 0);
                this.sky("day-viewport", 0, 0.55, 0.6);
                this.sky("day-viewport-below-horizon", 35, 0.55, 0.6);
                this.world.getServer().runOnServer(server -> server.overworld().setDayTime(18000));
                this.sky("night-window", 0, 0, 0);
                this.sky("night-viewport", 0, 0.55, 0.6);
                this.sky("night-viewport-tall", 0, 0.28, 0.85);
                this.world.getServer().runOnServer(server -> {
                    ServerLevel nether = server.getLevel(Level.NETHER);
                    if (nether == null) throw new IllegalStateException("The Nether is not loaded");
                    server.getPlayerList().getPlayers().getFirst().teleportTo(nether, 0, 200, 0, Set.of(), 0, 0, false);
                });
                this.waitUntil("Nether synchronized", client -> client.level.dimension().equals(Level.NETHER));
                this.world.getClientWorld().waitForChunksRender();
                this.sky("no-sky-window", 0, 0, 0);
                this.sky("no-sky-viewport", 0, 0.55, 0.6);
            }
        }

        private void sky(String name, float pitch, double widthFraction, double heightFraction) {
            this.context.runOnClient(client -> {
                client.player.setYRot(0);
                client.player.setXRot(pitch);
                if (widthFraction == 0) {
                    GameViewport.clear();
                } else {
                    Window window = client.getWindow();
                    int width = Math.max(64, (int) (window.getWidth() * widthFraction));
                    int height = Math.max(64, (int) (window.getHeight() * heightFraction));
                    GameViewport.set(new GameViewport.Bounds((window.getWidth() - width) / 2, (window.getHeight() - height) / 2, width, height));
                }
            });
            this.game.waitForWorldFrames(5);
            this.screenshot("sky-" + name);
        }
    }
}
