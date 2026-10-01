package fr.lacaleche.glue.testmod.gametest.render;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.gametest.IrisTest;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.lumos.Lumos;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

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
                this.context.runOnClient(client -> {
                    GameViewport.clear();
                    if (client.level != null) DemoLights.INSTANCE.clear(client.level);
                });
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

        protected void lookAt(double x, double y, double z) {
            this.context.runOnClient(client -> {
                Vec3 direction = new Vec3(x, y, z).subtract(client.player.getEyePosition());
                client.player.setYRot((float) Math.toDegrees(Math.atan2(direction.z, direction.x)) - 90);
                client.player.setXRot((float) -Math.toDegrees(Math.atan2(direction.y, Math.hypot(direction.x, direction.z))));
            });
        }

        protected long placeWorldLight() {
            long id = this.world.getServer().computeOnServer(server -> Lumos.place(server.overworld(), Light.point(5.5, 2, 9, 1, 0.45f, 0.2f, 3, 12)));
            this.waitUntil("persistent light synchronized", client -> !Lumos.active(client.level).isEmpty());
            return id;
        }
    }

    @ClientTestSpec(explicitOnly = true)
    public static final class IrisHud extends Arena {

        @Override
        protected void renderTest() {
            try (IrisTest iris = new IrisTest(this.context)) {
                iris.setEnabled(true);
                this.world.getServer().runOnServer(server -> {
                    ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                    player.getInventory().add(new ItemStack(Items.GLOWSTONE));
                    player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
                    player.getInventory().add(new ItemStack(Items.PRISMARINE));
                    player.inventoryMenu.broadcastChanges();
                });
                this.context.runOnClient(client -> {
                    Vec3 eye = client.player.getEyePosition();
                    Vec3 look = client.player.getViewVector(1);
                    DemoLights.INSTANCE.spawn(client.level, Light.point(eye.x + look.x * 3, eye.y, eye.z + look.z * 3, 1, 0.45f, 0.2f, 3, 12));
                });
                this.game.waitForWorldFrames(60);
                this.screenshot("hud-shaders-on-near-light");
                this.game.openInventory();
                this.screenshot("inventory-shaders-on");
                this.context.getInput().pressKey(options -> options.keyInventory);
                this.context.waitForScreen(null);
                iris.setEnabled(false);
                this.screenshot("hud-shaders-off-control");
                iris.setEnabled(true);
                this.screenshot("hud-shaders-reenabled");
            }
        }
    }

    @ClientTestSpec(explicitOnly = true)
    public static final class LumosSmoke extends Arena {

        @Override
        protected void renderTest() {
            long light = this.placeWorldLight();
            try (IrisTest iris = new IrisTest(this.context)) {
                iris.setEnabled(false);
                this.pose(9, 0, 13, 125, 15);
                this.game.waitForWorldFrames(60);
                this.screenshot("night-light-no-shader");
                iris.setEnabled(true);
                this.pose(9, 0, 13, 125, 15);
                this.game.waitForWorldFrames(60);
                this.screenshot("night-light-shader");
            } finally {
                this.world.getServer().runOnServer(server -> Lumos.remove(server.overworld(), light));
            }
        }
    }

    @ClientTestSpec(value = "albedo-issue", explicitOnly = true)
    public static final class Albedo extends Arena {

        @Override
        protected void renderTest() {
            long light = this.placeWorldLight();
            try (IrisTest iris = new IrisTest(this.context)) {
                for (boolean enabled : new boolean[] {false, true}) {
                    iris.setEnabled(enabled);
                    this.pose(9, 0, 13, 125, 15);
                    this.game.waitForWorldFrames(20);
                    this.screenshot(enabled ? "with-shader" : "without-shader");
                }
            } finally {
                this.world.getServer().runOnServer(server -> Lumos.remove(server.overworld(), light));
            }
        }
    }

    @ClientTestSpec(explicitOnly = true)
    public static final class GlassQuality extends Arena {

        @Override
        protected void renderTest() {
            int x = 10000;
            int y = 120;
            int z = 10000;
            this.pose(x + 0.5, y - 0.5, z - 4.5, 0, 0);
            this.world.getClientWorld().waitForChunksDownload();
            this.world.getServer().runOnServer(server -> glassRig(server.overworld(), x, y, z, false));
            try (IrisTest iris = new IrisTest(this.context)) {
                this.context.runOnClient(client -> DemoLights.INSTANCE.spawn(client.level,
                        Light.point(x + 0.5, y + 1.5, z + 3.5, 1, 0.95f, 0.85f, 3, 12)));
                this.world.getClientWorld().waitForChunksRender();
                for (boolean enabled : new boolean[] {false, true}) {
                    iris.setEnabled(enabled);
                    String suffix = enabled ? "-shader" : "-vanilla";
                    this.pose(x + 0.5, y - 0.5, z - 4.5, 0, 0);
                    this.lookAt(x + 0.5, y + 1.5, z + 0.5);
                    this.screenshot("backlit" + suffix);
                    this.lookAt(x + 0.5, y - 0.5, z - 1.5);
                    this.screenshot("floor-pool" + suffix);
                    this.pose(x + 0.5, y - 0.5, z + 6.5, 0, 0);
                    this.lookAt(x + 0.5, y + 1.5, z + 0.5);
                    this.screenshot("front-lit" + suffix);
                }
            } finally {
                this.world.getServer().runOnServer(server -> glassRig(server.overworld(), x, y, z, true));
            }
        }

        private static void glassRig(ServerLevel level, int bx, int by, int bz, boolean clear) {
            for (int x = -6; x <= 6; x++) {
                for (int z = -6; z <= 6; z++) level.setBlockAndUpdate(new BlockPos(bx + x, by - 1, bz + z),
                        (clear ? Blocks.AIR : Blocks.QUARTZ_SLAB).defaultBlockState());
            }
            Block[] columns = {Blocks.GLASS, Blocks.RED_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
                    Blocks.LIME_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE, Blocks.RED_STAINED_GLASS_PANE, Blocks.GLASS_PANE};
            for (int x = -6; x <= 6; x++) {
                Block column = x >= -3 && x <= 3 ? columns[x + 3] : Blocks.SMOOTH_STONE;
                for (int y = -1; y <= 3; y++) level.setBlockAndUpdate(new BlockPos(bx + x, by + y, bz),
                        (clear ? Blocks.AIR : y == 3 ? Blocks.SMOOTH_STONE : column).defaultBlockState());
            }
        }
    }

    @ClientTestSpec(value = "spot-perf", explicitOnly = true)
    public static final class SpotPerformance extends Arena {

        @Override
        protected void renderTest() {
            try (IrisTest iris = new IrisTest(this.context)) {
                this.pose(9, 0, 13, 0, 25);
                iris.setEnabled(true);
                this.probe("baseline");
                Light spot = this.context.computeOnClient(client -> {
                    Vec3 eye = client.player.getEyePosition();
                    Vec3 look = client.player.getViewVector(1);
                    return DemoLights.INSTANCE.spawn(client.level, Light.spot(eye.x, eye.y, eye.z,
                            (float) look.x, (float) look.y, (float) look.z, 0.95f, 0.921f, 0.77f, 3, 22, 20, 32));
                });
                this.probe("with-spot");
                this.context.runOnClient(client -> {
                    DemoLights.INSTANCE.remove(client.level, spot);
                    Vec3 eye = client.player.getEyePosition();
                    DemoLights.INSTANCE.spawn(client.level, Light.point(eye.x, eye.y, eye.z, 0.95f, 0.921f, 0.77f, 3, 12));
                });
                this.probe("with-point");
            }
        }

        private void probe(String label) {
            this.game.waitForWorldFrames(60);
            long total = 0;
            for (int tick = 0; tick < 60; tick++) {
                this.context.waitTick();
                total += this.context.computeOnClient(client -> client.getFps());
            }
            Testmod.LOGGER.info("Controlled Fabric run, avg FPS {}: {} (not a free-running benchmark)", label, total / 60);
        }
    }

    public static final class ViewportSky extends Arena {

        @Override
        protected void renderTest() {
            boolean hasIris = FabricLoader.getInstance().isModLoaded("iris");
            try (IrisTest iris = hasIris ? new IrisTest(this.context) : null) {
                if (iris != null) iris.setEnabled(false);
                this.context.runOnClient(client -> DemoLights.INSTANCE.clear(client.level));
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
