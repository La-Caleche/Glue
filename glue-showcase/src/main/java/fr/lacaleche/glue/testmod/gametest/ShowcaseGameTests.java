package fr.lacaleche.glue.testmod.gametest;

import com.mojang.blaze3d.platform.Window;
import fr.lacaleche.glue.client.file.FileDialogs;
import fr.lacaleche.glue.client.viewport.GameViewport;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.IrisShadersTool;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.testmod.TestmodClient;
import fr.lacaleche.glue.testmod.controls.ShowcaseControlScreen;
import fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport;
import fr.lacaleche.glue.testmod.gametest.mcsx.RealInput;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import fr.lacaleche.glue.testmod.registries.TestKeybinds;
import fr.lacaleche.glue.testmod.scene.BlockSceneTestScreen;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The showcase's in-game test definitions. Run one with
 * {@code gradlew :glue-showcase:runClient -Pglue.gametest=<name>
 * -Pglue.showcase.quickplay=<world>}, then read {@code screenshots/gametest/<name>/} back.
 */
@Environment(EnvType.CLIENT)
public final class ShowcaseGameTests {

    /** Level with the horizon, so the band between the two sky discs is in frame. */
    private static final float HORIZON_PITCH = 0.0f;
    /** Pitched below the horizon, so the dark disc and the terrain fill the frame. */
    private static final float GROUND_PITCH = 35.0f;

    private ShowcaseGameTests() {
    }

    public static void register() {
        GameTests.register("glue-test:native-dialogs", ShowcaseGameTests::nativeDialogs);
        GameTests.register("glue-test:iris-hud", ShowcaseGameTests::irisHud);
        GameTests.register("glue-test:lumos-smoke", ShowcaseGameTests::lumosSmoke);
        GameTests.register("glue-test:albedo-issue", ShowcaseGameTests::albedoIssue);
        GameTests.register("glue-test:glass-quality", ShowcaseGameTests::glassQuality);
        GameTests.register("glue-test:spot-perf", ShowcaseGameTests::spotPerf);
        GameTests.register("glue-test:viewport-sky", ShowcaseGameTests::viewportSky);
        GameTests.register("glue-test:showcase-smoke", ShowcaseGameTests::showcaseSmoke);
    }

    private static GameTest showcaseSmoke() {
        AtomicReference<ShowcaseControlScreen> controlsUi = new AtomicReference<>();
        AtomicReference<Screen> controls = new AtomicReference<>();
        GameTest test = GameTest.create("glue-test:showcase-smoke")
                .waitForWorld()
                .run("open the showcase controls", ctx -> {
                    controlsUi.set(ShowcaseControlScreen.open(ctx.client()));
                    controls.set(ctx.client().screen);
                })
                .waitUntil("the showcase controls open",
                        ctx -> controls.get() != null && ctx.client().screen == controls.get());
        McsxGameTestSupport.uiUntil(test, "wait until the showcase controls are laid out", () ->
                controlsUi.get().requireView().getWidth() > 0);
        McsxGameTestSupport.ui(test, "assert registered keybind chrome", () -> {
            Text openKey = McsxGameTestSupport.tagged(
                    controlsUi.get(), ShowcaseControlScreen.TAG_OPEN_KEY, Text.class
            );
            Text raycastKey = McsxGameTestSupport.tagged(
                    controlsUi.get(), ShowcaseControlScreen.TAG_RAYCAST_KEY, Text.class
            );
            McsxGameTestSupport.requireEquals(
                    TestKeybinds.openShowcaseKey().getString(),
                    openKey.getText().toString(),
                    "Open showcase binding"
            );
            McsxGameTestSupport.requireEquals(
                    TestKeybinds.toggleRaycastDebugKey().getString(),
                    raycastKey.getText().toString(),
                    "Raycast debug binding"
            );
        });
        test.waitTicks(5).screenshot("controls");
        McsxGameTestSupport.ui(test, "open the orbit scene from MCSX controls", () ->
                McsxGameTestSupport.tagged(
                        controlsUi.get(), ShowcaseControlScreen.TAG_ORBIT, Button.class
                ).performClick());
        test.waitUntil("the vanilla orbit scene opens",
                        ctx -> ctx.client().screen instanceof BlockSceneTestScreen)
                .waitTicks(40)
                .screenshot("orbit-scene")
                .run("close the orbit scene", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("the orbit scene closes", ctx -> ctx.client().screen == null)
                .run("reopen the showcase controls", ctx -> {
                    controlsUi.set(ShowcaseControlScreen.open(ctx.client()));
                    controls.set(ctx.client().screen);
                })
                .waitUntil("the showcase controls reopen",
                        ctx -> controls.get() != null && ctx.client().screen == controls.get());
        McsxGameTestSupport.ui(test, "open the file-dialog screen from MCSX controls", () ->
                McsxGameTestSupport.tagged(
                        controlsUi.get(), ShowcaseControlScreen.TAG_FILES, Button.class
                ).performClick());
        test.waitUntil("the file-dialog screen opens",
                        ctx -> ctx.client().screen != controls.get())
                .waitTicks(20)
                .screenshot("file-dialogs")
                .run("return from the file-dialog screen", ctx ->
                        RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("the file-dialog screen returns to controls",
                        ctx -> ctx.client().screen == controls.get());
        McsxGameTestSupport.ui(test, "close the showcase controls", () ->
                McsxGameTestSupport.tagged(
                        controlsUi.get(), ShowcaseControlScreen.TAG_CLOSE, Button.class
                ).performClick());
        test.waitUntil("the showcase controls close", ctx -> ctx.client().screen == null)
                .run("enable raycast debug", ctx -> TestmodClient.getInstance().toggleRaycastDebug())
                .waitTicks(5)
                .screenshot("raycast-debug")
                .run("disable raycast debug", ctx -> TestmodClient.getInstance().toggleRaycastDebug());
        return test;
    }

    /**
     * Opens each real platform picker. This is deliberately human-assisted: Minecraft input cannot
     * portably dismiss an OS-owned window, so cancel each dialog when it appears. The steps verify
     * that cancellation completes normally, and the runner's subsequent exit exercises NFD shutdown.
     */
    private static GameTest nativeDialogs() {
        GameTest test = GameTest.create("glue-test:native-dialogs");
        expectDialogCancellation(test, "cancel the native open-file dialog", () ->
                FileDialogs.showOpenDialog(null,
                        new FileDialogs.FileFilter("Images", "*.png", "jpg,jpeg")));
        expectDialogCancellation(test, "cancel the native save-file dialog", () ->
                FileDialogs.showSaveDialogInDefaultFolder("cancel-this-dialog.txt",
                        new FileDialogs.FileFilter("Text Files", ".txt", "md,")));
        return expectDialogCancellation(test, "cancel the native folder dialog",
                FileDialogs::showOpenFolderDialog);
    }

    private static GameTest expectDialogCancellation(GameTest test, String description,
                                                      Supplier<CompletableFuture<Optional<String>>> open) {
        return test.step(description, GameTest.LONG_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<Optional<String>> pending;

            @Override
            public boolean tick(TestContext context) {
                if (this.pending == null) this.pending = open.get();
                if (!this.pending.isDone()) return false;

                Optional<String> selection = this.pending.join();
                if (selection.isPresent()) {
                    throw new AssertionError("Expected the dialog to be cancelled, selected " + selection.get());
                }
                return true;
            }
        });
    }

    /**
     * Reproduces the reported Iris-mode HUD corruption: with a shaderpack active and a Lumos light
     * covering the player (the entity shadow re-render runs), hotbar and inventory item models were
     * rendered black. Captures the HUD and the inventory screen with shaders on, a control with
     * shaders off, and the HUD again after re-enabling &mdash; all in one unattended session.
     */
    private static GameTest irisHud() {
        return GameTest.create("glue-test:iris-hud")
                .waitForWorld()
                .tool(IrisShadersTool.ID, "true")
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
                .run("open inventory", ctx -> ctx.client().setScreen(
                        new InventoryScreen(ctx.player())
                ))
                .waitTicks(5)
                .screenshot("inventory-shaders-on")
                .closeScreen()
                .tool(IrisShadersTool.ID, "false")
                .waitTicks(20)
                .screenshot("hud-shaders-off-control")
                .tool(IrisShadersTool.ID, "true")
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
        GameTest test = GameTest.create("glue-test:lumos-smoke")
                .waitForWorld()
                .run("record viewpoint", ctx -> {
                    viewpoint[0] = ctx.player().getX();
                    viewpoint[1] = ctx.player().getY();
                    viewpoint[2] = ctx.player().getZ();
                    viewpoint[3] = ctx.player().getYRot();
                    viewpoint[4] = ctx.player().getXRot();
                })
                .tool(IrisShadersTool.ID, "false")
                .setDayTime(18000L)
                .waitTicks(60);
        restoreViewpoint(test, viewpoint)
                .waitTicks(5)
                .screenshot("night-light-no-shader")
                .tool(IrisShadersTool.ID, "true")
                .waitTicks(60);
        restoreViewpoint(test, viewpoint)
                .waitTicks(5)
                .screenshot("night-light-shader");
        return test.closeScreen();
    }

    /**
     * Puts the players back on the recorded viewpoint: the move is its own server step, so it has
     * executed before the aim step runs on the client. ({@code teleportTo} sends the move with its
     * rotation flagged relative, so it would not clobber an aim set beforehand either &mdash;
     * ordering the two steps just makes the framing independent of that detail.)
     */
    private static GameTest restoreViewpoint(GameTest test, double[] viewpoint) {
        return test
                .runOnServer("move to the recorded viewpoint", server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.teleportTo(viewpoint[0], viewpoint[1], viewpoint[2]);
                    }
                })
                .run("aim at the recorded viewpoint", ctx -> {
                    ctx.player().setYRot((float) viewpoint[3]);
                    ctx.player().setXRot((float) viewpoint[4]);
                });
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

        GameTest test = GameTest.create("glue-test:glass-quality")
                .waitForWorld()
                .selectEmptyHand()
                .teleport(wallX, standY, bz - 4.5)
                .waitUntil("rig chunks are loaded",
                        ctx -> ctx.level().getChunkSource().hasChunk(bx >> 4, bz >> 4))
                .runOnServer("build the glass rig",
                        server -> buildGlassRig(server.overworld(), bx, by, bz, false))
                .setDayTime(18000L)
                .run("spawn the rig light", ctx -> DemoLights.INSTANCE.spawn(ctx.player().level(),
                        Light.point(wallX, wallY, bz + 3.5, 1.0f, 0.95f, 0.85f, 3.0f, 12.0f)))
                .waitTicks(80);

        for (boolean shaders : new boolean[] {false, true}) {
            String suffix = shaders ? "-shader" : "-vanilla";
            test.tool(IrisShadersTool.ID, Boolean.toString(shaders));
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
     * numbers are emitted to the game log.
     */
    private static GameTest spotPerf() {
        Light[] spawned = new Light[1];
        return GameTest.create("glue-test:spot-perf")
                .waitForWorld()
                .selectEmptyHand()
                .teleport(9.0, 0.0, 13.0)
                .setDayTime(18000L)
                .run("aim at the terrain", ctx -> {
                    ctx.player().setYRot(0.0f);
                    ctx.player().setXRot(25.0f);
                })
                .tool(IrisShadersTool.ID, "true")
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

    /** Averages the FPS counter over 60 ticks and logs it under {@code label}. */
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
     * A/B of the world's placed light with shaders off, then on. Framing is pinned to a fixed arena
     * pose &mdash; teleport, then aim &mdash; and re-pinned before <em>every</em> screenshot: the
     * arena wither shoves the player between and during runs (and the drift persists into the save),
     * so pinning once at the start would still let the second shot frame a different scene.
     *
     * <p>The pin is a fixed pose rather than the nearest placed light on purpose: "nearest" would be
     * measured from wherever the player has drifted to, which is the drift this is correcting.</p>
     */
    private static GameTest albedoIssue() {
        GameTest test = GameTest.create("glue-test:albedo-issue")
                .waitForWorld()
                .selectEmptyHand()
                .setDayTime(18000L);
        pinToArenaViewpoint(test).waitTicks(20);

        for (boolean shaders : new boolean[] {false, true}) {
            test.tool(IrisShadersTool.ID, Boolean.toString(shaders));
            pinToArenaViewpoint(test)
                    .waitTicks(5)
                    .screenshot(shaders ? "with-shader" : "without-shader");
        }

        return test.closeScreen();
    }

    /** Puts the player on the arena's light-facing pose: the move first, then the aim. */
    private static GameTest pinToArenaViewpoint(GameTest test) {
        return test.teleport(9.0, 0.0, 13.0)
                .run("aim at the arena light", ctx -> {
                    ctx.player().setYRot(125.0f);
                    ctx.player().setXRot(15.0f);
                });
    }

    /**
     * Pairs viewport and full-window captures of the same sky from the same pose, so the viewport
     * composite can be read against the rendering it is meant to reproduce.
     *
     * <p>The world target's alpha is not coverage. The sky discs write alpha one, but the sun, moon
     * and stars overwrite it &mdash; {@code CELESTIAL} and {@code STARS} blend alpha with
     * {@code (ONE, ZERO)} &mdash; and the horizon band between the two discs keeps the clear's alpha
     * zero. The ordinary GUI texture shader discards zero-alpha samples, and its blended pipeline also
     * treats intermediate alpha as coverage; a complete world image must instead present every RGB
     * sample without blending or discard. Shaders stay off and the demo lights are cleared throughout:
     * Lumos writes alpha one in its own composite and would hide the defect.</p>
     *
     * <p>Clouds and the sunrise band need no capture of their own &mdash; both blend alpha with
     * {@code (ONE, ONE_MINUS_SRC_ALPHA)}, which saturates back to one over an opaque sky. The final
     * pair transfers the spectator above the Nether ceiling to cover a dimension with no sky.</p>
     */
    private static GameTest viewportSky() {
        double[] viewpoint = new double[3];
        double[] noSkyViewpoint = {0.0, 200.0, 0.0};
        GameType[] previousMode = new GameType[1];
        GameTest test = GameTest.create("glue-test:viewport-sky")
                .waitForWorld()
                .selectEmptyHand()
                .tool(IrisShadersTool.ID, "false")
                .run("clear the demo lights", ctx -> DemoLights.INSTANCE.clear(ctx.player().level()))
                .expect("no demo light is lit", ctx -> DemoLights.INSTANCE.spawned().isEmpty())
                // Spectator, so the pose survives each capture: the player would otherwise fall out of
                // it between pins, and six unbroken falls add up to a death mid-test.
                .runOnServer("watch the sky as a spectator", server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        previousMode[0] = player.gameMode();
                        player.setGameMode(GameType.SPECTATOR);
                    }
                })
                .run("record a viewpoint above the terrain", ctx -> {
                    viewpoint[0] = ctx.player().getX();
                    viewpoint[1] = Math.max(ctx.player().getY() + 80.0, 160.0);
                    viewpoint[2] = ctx.player().getZ();
                })
                .setDayTime(6000L)
                .waitTicks(40);

        captureSky(test, viewpoint, HORIZON_PITCH, null, "day-window");
        captureSky(test, viewpoint, HORIZON_PITCH, centredViewport(0.55, 0.6), "day-viewport");
        captureSky(test, viewpoint, GROUND_PITCH, centredViewport(0.55, 0.6), "day-viewport-below-horizon");

        test.setDayTime(18000L).waitTicks(40);
        captureSky(test, viewpoint, HORIZON_PITCH, null, "night-window");
        captureSky(test, viewpoint, HORIZON_PITCH, centredViewport(0.55, 0.6), "night-viewport");
        captureSky(test, viewpoint, HORIZON_PITCH, centredViewport(0.28, 0.85), "night-viewport-tall");

        test.runOnServer("move to a dimension without a sky", server -> {
            ServerLevel nether = server.getLevel(Level.NETHER);
            if (nether == null) throw new IllegalStateException("The Nether is not loaded");

            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(nether, noSkyViewpoint[0], noSkyViewpoint[1], noSkyViewpoint[2],
                        Set.of(), 0.0f, HORIZON_PITCH, false);
            }
        }).waitUntil("the no-sky dimension is loaded", ctx ->
                ctx.player().level().dimension().equals(Level.NETHER)).waitTicks(40);
        captureSky(test, noSkyViewpoint, HORIZON_PITCH, null, "no-sky-window");
        captureSky(test, noSkyViewpoint, HORIZON_PITCH, centredViewport(0.55, 0.6), "no-sky-viewport");

        return test
                .run("return the world to the whole window", ctx -> GameViewport.clear())
                .runOnServer("return to the overworld", server -> {
                    ServerLevel overworld = server.overworld();
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.teleportTo(overworld, viewpoint[0], viewpoint[1], viewpoint[2],
                                Set.of(), 0.0f, HORIZON_PITCH, false);
                    }
                })
                .waitUntil("the overworld is restored", ctx ->
                        ctx.player().level().dimension().equals(Level.OVERWORLD))
                .runOnServer("restore the game mode", server -> {
                    if (previousMode[0] == null) return;

                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.setGameMode(previousMode[0]);
                    }
                });
    }

    /**
     * Pins the pose, applies or clears the viewport, and captures. The pin is re-applied for every
     * capture because the teleport is a server step and the aim is a client one: only re-establishing
     * both together makes two frames comparable.
     *
     * @param bounds the viewport to confine the world to, or null to render the whole window
     */
    private static void captureSky(GameTest test, double[] viewpoint, float pitch,
                                   Function<TestContext, GameViewport.Bounds> bounds, String label) {
        test.runOnServer("move to the sky viewpoint for '" + label + "'", server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(viewpoint[0], viewpoint[1], viewpoint[2]);
            }
        });
        test.run("aim for '" + label + "'", ctx -> {
            ctx.player().setYRot(0.0f);
            ctx.player().setXRot(pitch);
        });
        test.run(bounds == null
                ? "render '" + label + "' in the whole window"
                : "confine '" + label + "' to a viewport", ctx -> {
            if (bounds == null) GameViewport.clear();
            else GameViewport.set(bounds.apply(ctx));
        });
        test.waitTicks(5).screenshot("sky-" + label);
    }

    /** A centred viewport covering the given fractions of the window, in framebuffer pixels. */
    private static Function<TestContext, GameViewport.Bounds> centredViewport(double widthFraction,
                                                                             double heightFraction) {
        return ctx -> {
            Window window = ctx.client().getWindow();
            int width = Math.max(64, (int) (window.getWidth() * widthFraction));
            int height = Math.max(64, (int) (window.getHeight() * heightFraction));
            return new GameViewport.Bounds(
                    (window.getWidth() - width) / 2, (window.getHeight() - height) / 2, width, height);
        };
    }
}
