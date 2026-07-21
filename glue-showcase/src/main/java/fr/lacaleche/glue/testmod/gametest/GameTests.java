package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The showcase's in-game test definitions. Run one with
 * {@code gradlew :glue-showcase:runClient -Pglue.showcase.gametest=<name>
 * -Pglue.showcase.quickplay=<world>}, then read {@code screenshots/gametest/<name>/} back.
 */
@Environment(EnvType.CLIENT)
final class GameTests {

    private GameTests() {
    }

    static void registerAll() {
        GameTestRunner.register(irisHud());
        GameTestRunner.register(lumosSmoke());
        GameTestRunner.register(albedoIssue());
        GameTestRunner.register(glassQuality());
        GameTestRunner.register(spotPerf());
    }

    /**
     * Reproduces the reported Iris-mode HUD corruption: with a shaderpack active and a Lumos light
     * covering the player (the entity shadow re-render runs), hotbar and inventory item models were
     * rendered black. Captures the HUD and the inventory screen with shaders on, a control with
     * shaders off, and the HUD again after re-enabling &mdash; all in one unattended session.
     */
    private static GameTest irisHud() {
        return GameTest.create("iris-hud")
                .waitForWorld()
                .expect("Iris is loaded", ctx -> IrisHooks.loaded())
                .enableShaders()
                .give(Items.GLOWSTONE, Items.DIAMOND_SWORD, Items.PRISMARINE)
                .setDayTime(18000L)
                .run("spawn a point light covering the player", ctx -> {
                    Vec3 eye = ctx.player().getEyePosition();
                    Vec3 look = ctx.player().getViewVector(1.0f);
                    DemoLights.INSTANCE.spawn(ctx.player().level(), Light.point(
                            eye.x + look.x * 3.0, eye.y, eye.z + look.z * 3.0,
                            1.0f, 0.45f, 0.2f, 3.0f, 12.0f));
                })
                .waitTicks(60)
                .screenshot("hud-shaders-on-near-light")
                .openInventory()
                .waitTicks(5)
                .screenshot("inventory-shaders-on")
                .closeScreen()
                .disableShaders()
                .waitTicks(20)
                .screenshot("hud-shaders-off-control")
                .enableShaders()
                .waitTicks(40)
                .screenshot("hud-shaders-reenabled");
    }

    /**
     * A/B of the same persistent world light with the shaderpack off, then on. The viewpoint is
     * recorded at world load and restored before each screenshot: the arena's wither shoves the
     * player (and the drifted position persists into the save on quit), so without pinning no two
     * shots frame the same scene.
     */
    private static GameTest lumosSmoke() {
        double[] viewpoint = new double[5];
        java.util.function.Consumer<TestContext> restore = ctx -> {
            ctx.server().execute(() -> {
                for (net.minecraft.server.level.ServerPlayer player
                        : ctx.server().getPlayerList().getPlayers()) {
                    player.teleportTo(viewpoint[0], viewpoint[1], viewpoint[2]);
                }
            });
            ctx.player().setYRot((float) viewpoint[3]);
            ctx.player().setXRot((float) viewpoint[4]);
        };
        return GameTest.create("lumos-smoke")
                .waitForWorld()
                .run("record viewpoint", ctx -> {
                    viewpoint[0] = ctx.player().getX();
                    viewpoint[1] = ctx.player().getY();
                    viewpoint[2] = ctx.player().getZ();
                    viewpoint[3] = ctx.player().getYRot();
                    viewpoint[4] = ctx.player().getXRot();
                })
                .disableShaders()
                .setDayTime(18000L)
                .waitTicks(60)
                .run("restore viewpoint", restore)
                .waitTicks(5)
                .screenshot("night-light-no-shader")
                .enableShaders()
                .waitTicks(60)
                .run("restore viewpoint", restore)
                .waitTicks(5)
                .screenshot("night-light-shader")
                .closeScreen();
    }

    /**
     * Self-contained glass rig, 10 km from the arena so nothing photobombs: a slab platform (slabs,
     * so nothing spawns on it at night), a 7-column wall &mdash; clear glass, red/blue/lime stained
     * glass, purple/red stained panes, clear pane &mdash; and a warm point light 3 blocks behind it.
     * Captures the three glass responses (backlit transmission glow, front-lit scatter + glint, the
     * transmitted coloured pool on the floor) with shaders off then on, and removes the rig after.
     */
    private static GameTest glassQuality() {
        int bx = 10000;
        int by = 120;
        int bz = 10000;
        double wallX = bx + 0.5;
        double wallY = by + 1.5;
        double standY = by - 0.5;

        GameTest test = GameTest.create("glass-quality")
                .waitForWorld()
                .selectEmptyHand()
                .teleport(wallX, standY, bz - 4.5)
                .waitUntil("rig chunks are loaded",
                        ctx -> ctx.level().hasChunkAt(new BlockPos(bx, by, bz)))
                .runOnServer("build the glass rig",
                        server -> buildGlassRig(server.overworld(), bx, by, bz, false))
                .setDayTime(18000L)
                .run("spawn the rig light", ctx -> DemoLights.INSTANCE.spawn(ctx.player().level(),
                        Light.point(wallX, wallY, bz + 3.5, 1.0f, 0.95f, 0.85f, 3.0f, 12.0f)))
                .waitTicks(80);

        for (boolean shaders : new boolean[] {false, true}) {
            String suffix = shaders ? "-shader" : "-vanilla";
            if (shaders) {
                test.enableShaders();
            } else {
                test.disableShaders();
            }
            test.teleport(wallX, standY, bz - 4.5)
                    .lookAt(wallX, wallY, bz + 0.5)
                    .waitTicks(5)
                    .screenshot("backlit" + suffix)
                    .lookAt(wallX, by - 0.5, bz - 1.5)
                    .waitTicks(5)
                    .screenshot("floor-pool" + suffix)
                    .teleport(wallX, standY, bz + 6.5)
                    .lookAt(wallX, wallY, bz + 0.5)
                    .waitTicks(5)
                    .screenshot("front-lit" + suffix);
        }

        return test.runOnServer("remove the glass rig",
                server -> buildGlassRig(server.overworld(), bx, by, bz, true));
    }

    /**
     * Measures the shaderpack-frame cost of one spot light: average FPS over 60 ticks with the
     * pack on, then again after spawning an eye-level spot aimed down at the arena terrain (the
     * reported 90-to-30 FPS case), then a point light of the demo range for comparison. The
     * numbers land in {@code report.txt} via the step log.
     */
    private static GameTest spotPerf() {
        Light[] spawned = new Light[1];
        return GameTest.create("spot-perf")
                .waitForWorld()
                .selectEmptyHand()
                .teleport(9.0, 0.0, 13.0)
                .setDayTime(18000L)
                .run("aim at the terrain", ctx -> {
                    ctx.player().setYRot(0.0f);
                    ctx.player().setXRot(25.0f);
                })
                .enableShaders()
                .waitTicks(60)
                .step("measure baseline fps", 300, fpsProbe("baseline"))
                .run("spawn an eye-level spot light", ctx -> {
                    Vec3 eye = ctx.player().getEyePosition();
                    Vec3 look = ctx.player().getViewVector(1.0f);
                    spawned[0] = DemoLights.INSTANCE.spawn(ctx.player().level(), Light.spot(
                            eye.x, eye.y, eye.z,
                            (float) look.x, (float) look.y, (float) look.z,
                            0.95f, 0.921f, 0.77f, 3.0f, 22.0f, 20.0f, 32.0f));
                })
                .waitTicks(60)
                .step("measure spot fps", 300, fpsProbe("with-spot"))
                .run("swap the spot for a point light", ctx -> {
                    DemoLights.INSTANCE.remove(ctx.player().level(), spawned[0]);
                    Vec3 eye = ctx.player().getEyePosition();
                    spawned[0] = DemoLights.INSTANCE.spawn(ctx.player().level(), Light.point(
                            eye.x, eye.y, eye.z, 0.95f, 0.921f, 0.77f, 3.0f, 12.0f));
                })
                .waitTicks(60)
                .step("measure point fps", 300, fpsProbe("with-point"))
                .run("remove the point light", ctx ->
                        DemoLights.INSTANCE.remove(ctx.player().level(), spawned[0]));
    }

    /** Averages the FPS counter over 60 ticks and logs it into the report under {@code label}. */
    private static GameTest.StepTick fpsProbe(String label) {
        int[] ticks = new int[1];
        long[] sum = new long[1];
        return ctx -> {
            sum[0] += ctx.client().getFps();
            if (++ticks[0] < 60) return false;
            ctx.log("avg fps " + label + ": " + (sum[0] / 60));
            return true;
        };
    }

    /**
     * Builds (or, with {@code clear}, removes) the glass-quality rig around its base position.
     * The wall spans the full platform width and reaches down to the floor: glass only in the
     * centre seven columns, smooth stone elsewhere &mdash; light may only cross the wall THROUGH
     * glass, so every colour that lands on the camera-side floor is transmitted colour, not spill
     * around the edges or through the slab gap.
     */
    private static void buildGlassRig(ServerLevel level, int bx, int by, int bz, boolean clear) {
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                level.setBlockAndUpdate(new BlockPos(bx + x, by - 1, bz + z),
                        (clear ? Blocks.AIR : Blocks.QUARTZ_SLAB).defaultBlockState());
            }
        }
        Block[] columns = {
                Blocks.GLASS, Blocks.RED_STAINED_GLASS, Blocks.BLUE_STAINED_GLASS,
                Blocks.LIME_STAINED_GLASS, Blocks.PURPLE_STAINED_GLASS_PANE,
                Blocks.RED_STAINED_GLASS_PANE, Blocks.GLASS_PANE,
        };
        for (int x = -6; x <= 6; x++) {
            Block column = x >= -3 && x <= 3 ? columns[x + 3] : Blocks.SMOOTH_STONE;
            for (int y = -1; y <= 3; y++) {
                Block block = clear ? Blocks.AIR : (y == 3 ? Blocks.SMOOTH_STONE : column);
                level.setBlockAndUpdate(new BlockPos(bx + x, by + y, bz),
                        block.defaultBlockState());
            }
        }
    }

    /**
     * A/B of the world's placed light with shaders off, then on. Framing is anchored to the
     * LIGHT, not the player: the arena wither shoves the player between and during runs (and the
     * drift persists into the save), so before every screenshot the player is teleported a few
     * blocks south of the nearest placed light and aimed at it &mdash; every run frames the same
     * scene regardless of what the save last recorded.
     */
    private static GameTest albedoIssue() {
        GameTest test = GameTest.create("albedo-issue")
                .waitForWorld()
                .selectEmptyHand()
                .setDayTime(18000L)
                .teleport(9.0, 0.0, 13.0)
                .waitTicks(20)
                .run("rotate player", ctx -> {
                    ctx.player().setYRot(125.0f);
                    ctx.player().setXRot(15.0f);
                })
                .disableShaders()
                .waitTicks(2)
                .screenshot("without-shader")
                .enableShaders()
                .waitTicks(2)
                .screenshot("with-shader")
                .closeScreen();
        return test;
    }
}
