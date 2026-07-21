package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.lumos.Light;
import fr.lacaleche.glue.testmod.lumos.DemoLights;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.item.Items;
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

    private static GameTest albedoIssue() {
        return GameTest.create("albedo-issue")
                .waitForWorld()
                .setDayTime(18000L)
                // Wireframe proof of where the world's placed light actually is, independent of
                // whether the lighting pipeline renders it.
                .lightPreview(true)
                .waitTicks(5)
                .screenshot("light-preview")
                .lightPreview(false)
                .disableShaders()
                .waitTicks(20)
                .screenshot("without-shader")
                .enableShaders()
                .waitTicks(20)
                .screenshot("with-shader")
                .closeScreen();
    }
}
