package fr.lacaleche.glue.testmod.gametest.mcsx.expedition;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.mcsx.client.component.Button;
import fr.lacaleche.glue.mcsx.client.component.Checkbox;
import fr.lacaleche.glue.mcsx.client.component.Text;
import fr.lacaleche.glue.mcsx.client.component.TextField;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionDemo;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionPlanner;
import fr.lacaleche.glue.testmod.mcsx.expedition.ExpeditionSession;
import fr.lacaleche.glue.testmod.registries.TestItems;
import fr.lacaleche.mui.MuiApi;
import fr.lacaleche.mui.OverlayHandle;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.view.View;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.concurrent.atomic.AtomicReference;

import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.require;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireEquals;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.requireSame;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.tagged;
import static fr.lacaleche.glue.testmod.gametest.mcsx.McsxGameTestSupport.ui;

public final class ExpeditionGameTest {

    private ExpeditionGameTest() {
    }

    public static void register() {
        GameTests.register("glue-test:mcsx-expedition", ExpeditionGameTest::create);
    }

    private static GameTest create() {
        ExpeditionDemo demo = ExpeditionDemo.INSTANCE;
        ExpeditionSession session = ExpeditionSession.INSTANCE;
        double[] start = new double[3];
        int[] elevationTarget = new int[1];
        int[] suppliesTarget = new int[1];
        AtomicReference<View> hudRoot = new AtomicReference<>();
        AtomicReference<OverlayHandle> foreignOverlay = new AtomicReference<>();

        GameTest test = GameTest.create("glue-test:mcsx-expedition")
                .waitForWorld()
                .run("reset expedition session", ctx -> session.reset())
                .waitTicks(5)
                .run("open expedition planner", ctx -> {
                    start[0] = ctx.player().getX();
                    start[1] = ctx.player().getY();
                    start[2] = ctx.player().getZ();
                    elevationTarget[0] = ctx.player().getBlockY() + 8;
                    suppliesTarget[0] = ExpeditionSession.countSupplies(ctx.player().getInventory()) + 3;
                    demo.openPlanner(ctx.client());
                })
                .waitUntil("expedition planner is open", ctx -> ctx.client().screen != null)
                .waitTicks(10);

        ui(test, "configure expedition through planner fields", () -> {
            ExpeditionPlanner planner = demo.planner();
            tagged(planner, ExpeditionPlanner.TAG_NAME, TextField.class).setText("Ridge Survey");
            tagged(planner, ExpeditionPlanner.TAG_TRAVEL_TARGET, TextField.class).setText("12");
            tagged(planner, ExpeditionPlanner.TAG_ELEVATION_TARGET, TextField.class)
                    .setText("disabled target");
            tagged(planner, ExpeditionPlanner.TAG_ELEVATION_ENABLED, Checkbox.class).setChecked(false);
            tagged(planner, ExpeditionPlanner.TAG_SUPPLIES_TARGET, TextField.class)
                    .setText(Integer.toString(suppliesTarget[0]));
        });
        test.waitTicks(5);

        // The slot can be taken while the planner sits open, so the start itself rechecks ownership.
        test.run("mount a foreign overlay under the open planner", ctx ->
                foreignOverlay.set(MuiApi.mountOverlay(new Fragment())));
        test.waitTicks(5);
        ui(test, "submit the planner under the foreign overlay", () -> {
            Button startButton = tagged(demo.planner(), ExpeditionPlanner.TAG_START, Button.class);
            require(startButton.isEnabled(), "Valid expedition Start button is disabled");
            startButton.performClick();
        });
        test.waitTicks(10);
        test.run("assert the refused start changed nothing", ctx -> {
            require(ctx.client().screen != null, "The planner closed on a start it could not make");
            requireSame(ExpeditionSession.Stage.DRAFT, session.stage(), "expedition stage");
            require(!demo.hud().isMounted(), "A refused start mounted the expedition HUD");
            require(!foreignOverlay.get().isClosed(), "The refused start closed the foreign overlay");
        });
        test.run("release the foreign overlay", ctx -> foreignOverlay.get().close());
        test.waitTicks(5);
        ui(test, "restore the elevation objective", () -> {
            ExpeditionPlanner planner = demo.planner();
            tagged(planner, ExpeditionPlanner.TAG_ELEVATION_TARGET, TextField.class)
                    .setText(Integer.toString(elevationTarget[0]));
            tagged(planner, ExpeditionPlanner.TAG_ELEVATION_ENABLED, Checkbox.class).setChecked(true);
        });

        ui(test, "start configured expedition", () -> {
            Button startButton = tagged(demo.planner(), ExpeditionPlanner.TAG_START, Button.class);
            require(startButton.isEnabled(), "Valid expedition Start button is disabled");
            startButton.performClick();
        });
        test.waitUntil("planner closes after expedition start", ctx -> ctx.client().screen == null)
                .waitUntil("expedition becomes active", ctx -> session.stage() == ExpeditionSession.Stage.ACTIVE)
                .waitTicks(10);
        ui(test, "assert active expedition HUD", () -> {
            View root = demo.hud().fragment().requireView();
            hudRoot.set(root);
            require(root.isShown(), "Expedition HUD is not visible in gameplay");
            Text title = tagged(demo.hud().fragment(), ExpeditionDemo.TAG_HUD_TITLE, Text.class);
            requireEquals("Ridge Survey", title.getText().toString(), "HUD expedition name");
            Text progress = tagged(demo.hud().fragment(), ExpeditionDemo.TAG_HUD_PROGRESS, Text.class);
            requireEquals("0 / 3", progress.getText().toString(), "initial HUD progress");
        });
        test.run("reject second expedition overlay", ctx -> {
                    try {
                        MuiApi.mountOverlay(new Fragment());
                    } catch (IllegalStateException exception) {
                        requireEquals(
                                "A ModernUI overlay is already active",
                                exception.getMessage(),
                                "duplicate overlay failure"
                        );
                        return;
                    }
                    throw new AssertionError("A duplicate ModernUI overlay was accepted");
                })
                .screenshot("expedition-active-hud")
                .run("reopen active expedition planner", ctx -> demo.openPlanner(ctx.client()))
                .waitUntil("active expedition planner is open", ctx -> ctx.client().screen != null)
                .waitTicks(10)
                .screenshot("expedition-active-planner");
        ui(test, "assert planner and HUD share active state", () -> {
            require(!hudRoot.get().isShown(), "Expedition HUD remains shown over planner");
            Text progress = tagged(demo.planner(), ExpeditionPlanner.TAG_PROGRESS, Text.class);
            requireEquals("0 / 3", progress.getText().toString(), "planner progress");
        });
        ui(test, "close active expedition planner", () ->
                tagged(demo.planner(), ExpeditionPlanner.TAG_CLOSE, Button.class).performClick());
        test.waitUntil("active planner closes", ctx -> ctx.client().screen == null).waitTicks(10);
        ui(test, "assert expedition HUD resumes", () -> {
            requireSame(hudRoot.get(), demo.hud().fragment().requireView(), "HUD root");
            require(hudRoot.get().isShown(), "Expedition HUD did not resume");
        });
        test.run("reopen planner to abort expedition", ctx -> demo.openPlanner(ctx.client()))
                .waitUntil("planner is open for abort", ctx -> ctx.client().screen != null)
                .waitTicks(5);
        ui(test, "abort active expedition", () ->
                tagged(demo.planner(), ExpeditionPlanner.TAG_ABORT, Button.class).performClick());
        test.waitUntil("expedition becomes aborted", ctx -> session.stage() == ExpeditionSession.Stage.ABORTED)
                .waitTicks(10);
        ui(test, "assert abort removed HUD and retained plan", () -> {
            require(!demo.hud().isMounted(), "Expedition HUD remains mounted after abort");
            require(hudRoot.get().getParent() == null, "Aborted expedition HUD remains attached");
            TextField name = tagged(demo.planner(), ExpeditionPlanner.TAG_NAME, TextField.class);
            requireEquals("Ridge Survey", name.getText().toString(), "aborted expedition name");
            tagged(demo.planner(), ExpeditionPlanner.TAG_START, Button.class).performClick();
        });
        test.waitUntil("restarted planner closes", ctx -> ctx.client().screen == null)
                .waitUntil("aborted expedition restarts", ctx -> session.stage() == ExpeditionSession.Stage.ACTIVE)
                .waitTicks(10);
        ui(test, "assert restarted expedition mounted a new HUD", () -> {
            View restartedRoot = demo.hud().fragment().requireView();
            require(restartedRoot != hudRoot.get(), "Restart reused the detached HUD root");
            require(restartedRoot.isShown(), "Restarted expedition HUD is not shown");
            hudRoot.set(restartedRoot);
        });

        test.runOnServer("complete travel objective", server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.teleportTo(start[0] + 14.0, start[1], start[2]);
                    }
                })
                .waitUntil("travel objective completes", ctx -> session.progress().travelComplete())
                .runOnServer("complete elevation objective", server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.teleportTo(start[0] + 14.0, elevationTarget[0] + 1.0, start[2]);
                    }
                })
                .waitUntil("elevation objective completes", ctx -> session.progress().elevationComplete())
                .runOnServer("complete supplies objective", server -> {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        player.getInventory().add(new ItemStack(TestItems.TEST_COMPONENT_ITEM, 3));
                    }
                })
                .waitUntil("supplies objective completes", ctx -> session.progress().suppliesComplete())
                .waitUntil("expedition completes", ctx -> session.stage() == ExpeditionSession.Stage.COMPLETED)
                .waitTicks(10);
        ui(test, "assert completed expedition HUD", () -> {
            Text progress = tagged(demo.hud().fragment(), ExpeditionDemo.TAG_HUD_PROGRESS, Text.class);
            requireEquals("3 / 3", progress.getText().toString(), "completed HUD progress");
        });
        test.selectEmptyHand()
                .waitTicks(10)
                .screenshot("expedition-completed-hud")
                .run("open completed expedition planner", ctx -> demo.openPlanner(ctx.client()))
                .waitUntil("completed expedition planner is open", ctx -> ctx.client().screen != null)
                .waitTicks(10);
        ui(test, "assert completed state survived planner reopen", () -> {
            Text progress = tagged(demo.planner(), ExpeditionPlanner.TAG_PROGRESS, Text.class);
            requireEquals("3 / 3", progress.getText().toString(), "completed planner progress");
            tagged(demo.planner(), ExpeditionPlanner.TAG_RESET_PROGRESS, Button.class).performClick();
        });
        test.waitUntil("expedition resets to draft", ctx -> session.stage() == ExpeditionSession.Stage.DRAFT)
                .waitTicks(10);
        ui(test, "assert reset detached HUD and restored draft", () -> {
            require(!demo.hud().isMounted(), "Expedition HUD remains mounted after reset");
            require(hudRoot.get().getParent() == null, "Expedition HUD root remains attached after reset");
            TextField name = tagged(demo.planner(), ExpeditionPlanner.TAG_NAME, TextField.class);
            requireEquals("Highland Survey", name.getText().toString(), "reset expedition name");
        });
        ui(test, "close reset expedition planner", () ->
                tagged(demo.planner(), ExpeditionPlanner.TAG_CLOSE, Button.class).performClick());
        test.waitUntil("reset expedition planner closes", ctx -> ctx.client().screen == null);
        return test;
    }
}
