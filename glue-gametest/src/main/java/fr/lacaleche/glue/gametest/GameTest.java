package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A scripted in-game test: a named, ordered list of steps executed one per client tick by
 * {@link GameTestRunner}. Built fluently &mdash;
 * {@code GameTest.create("mod:my-test").fullscreen().openWorld("Demo").screenshot("hud")} &mdash;
 * each step either completes, times out, or throws; the runner records the outcome per step and
 * writes a report next to the screenshots when the run ends.
 *
 * <p>A step is a {@link StepTick} polled every tick until it returns {@code true}. One-shot actions
 * ({@link #run}, {@link #give}) complete on their first tick; waits ({@link #waitUntil}) poll until
 * their condition holds. Only game-agnostic verbs live here &mdash; anything mod-specific goes
 * through {@link #tool}, which invokes a {@link GameTool} the owning mod registered in
 * {@link GameTools}, or through the raw {@link #step} escape hatch.</p>
 *
 * <p>Steps carry per-run closure state, so a {@code GameTest} instance runs once per game launch
 * &mdash; exactly how the runner uses it.</p>
 */
@Environment(EnvType.CLIENT)
public final class GameTest {

    /** Polled once per client tick; returns true when the step is complete. */
    @FunctionalInterface
    public interface StepTick {
        boolean tick(TestContext ctx) throws Exception;
    }

    public record Step(String description, int timeoutTicks, StepTick tick) {
    }

    /** 30 s at 20 TPS: generous for anything that is not world loading or a tool. */
    public static final int DEFAULT_TIMEOUT = 600;
    /** 2 min: world join and tool invocations (a mod opening a whole UI) legitimately take long. */
    public static final int LONG_TIMEOUT = 2400;

    private final String name;
    private final List<Step> steps = new ArrayList<>();

    private GameTest(String name) {
        this.name = name;
    }

    /** Names are namespaced by convention ({@code "ignis:editor-smoke"}) to avoid collisions. */
    public static GameTest create(String name) {
        return new GameTest(name);
    }

    public String name() {
        return name;
    }

    public List<Step> steps() {
        return List.copyOf(steps);
    }

    /** Raw step: full control over description, timeout and per-tick logic. */
    public GameTest step(String description, int timeoutTicks, StepTick tick) {
        steps.add(new Step(description, timeoutTicks, tick));
        return this;
    }

    /**
     * Invokes the registered {@link GameTool} {@code id} with {@code args} &mdash; the extension
     * point mods use to contribute high-level verbs ("open my editor", "load this file") without
     * this DSL knowing them. The tool is resolved lazily at execution time, so registration order
     * between mods does not matter; an unregistered id fails the step with the registered list.
     */
    public GameTest tool(String id, String... args) {
        List<String> arguments = List.of(args);
        String description = "tool " + id + (arguments.isEmpty() ? "" : " " + arguments);
        StepTick[] started = new StepTick[1];
        return step(description, LONG_TIMEOUT, ctx -> {
            if (started[0] == null) {
                started[0] = GameTools.require(id).start(ctx, arguments);
            }
            return started[0].tick(ctx);
        });
    }

    /** Runs an action once on the client thread. */
    public GameTest run(String description, Consumer<TestContext> action) {
        return step(description, DEFAULT_TIMEOUT, ctx -> {
            action.accept(ctx);
            return true;
        });
    }

    /** Schedules an action on the integrated server thread and waits until it has executed. */
    public GameTest runOnServer(String description, Consumer<MinecraftServer> action) {
        boolean[] submitted = new boolean[1];
        boolean[] done = new boolean[1];
        return step(description, DEFAULT_TIMEOUT, ctx -> {
            if (!submitted[0]) {
                submitted[0] = true;
                MinecraftServer server = ctx.server();
                server.execute(() -> {
                    action.accept(server);
                    done[0] = true;
                });
            }
            return done[0];
        });
    }

    public GameTest waitTicks(int ticks) {
        int[] counter = new int[1];
        return step("wait " + ticks + " ticks", ticks + 100, ctx -> ++counter[0] >= ticks);
    }

    public GameTest waitUntil(String description, Predicate<TestContext> condition) {
        return waitUntil(description, condition, DEFAULT_TIMEOUT);
    }

    public GameTest waitUntil(String description, Predicate<TestContext> condition, int timeoutTicks) {
        return step("wait until " + description, timeoutTicks, condition::test);
    }

    /** Fails the test immediately if the check does not hold. */
    public GameTest expect(String description, Predicate<TestContext> check) {
        return step("expect: " + description, DEFAULT_TIMEOUT, ctx -> {
            if (!check.test(ctx)) {
                throw new AssertionError("expectation failed: " + description);
            }
            return true;
        });
    }

    /** Switches the window to fullscreen (no-op if it already is). */
    public GameTest fullscreen() {
        return run("switch to fullscreen", ctx -> {
            if (!ctx.client().getWindow().isFullscreen()) {
                ctx.client().getWindow().toggleFullScreen();
            }
        });
    }

    /**
     * Opens the singleplayer world {@code levelName} from the title screen and waits for the join
     * to finish &mdash; the programmatic equivalent of {@code --quickPlaySingleplayer}, so a test
     * run needs no launch-argument wiring.
     */
    public GameTest openWorld(String levelName) {
        boolean[] requested = new boolean[1];
        boolean[] failed = new boolean[1];
        step("open world '" + levelName + "'", LONG_TIMEOUT, ctx -> {
            if (failed[0]) {
                throw new IllegalStateException("world '" + levelName + "' could not be opened");
            }
            if (!requested[0]) {
                requested[0] = true;
                ctx.client().createWorldOpenFlows().openWorld(levelName, () -> failed[0] = true);
            }
            return ctx.client().level != null;
        });
        return waitForWorld();
    }

    /** Waits for level + player + no loading screen, then a settling delay past the join haze. */
    public GameTest waitForWorld() {
        waitUntil("world and player are loaded",
                ctx -> ctx.client().level != null && ctx.client().player != null
                        && ctx.client().screen == null,
                LONG_TIMEOUT);
        return waitTicks(40);
    }

    /** Adds one of each item to every player's inventory (first free slots, i.e. the hotbar). */
    public GameTest give(Item... items) {
        List<Item> list = List.of(items);
        return runOnServer("give " + list, server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                for (Item item : list) {
                    player.getInventory().add(new ItemStack(item));
                }
            }
        });
    }

    /** Sets the overworld day time; 18000 is midnight. */
    public GameTest setDayTime(long time) {
        return runOnServer("set day time to " + time,
                server -> server.overworld().setDayTime(time));
    }

    public GameTest teleport(double x, double y, double z) {
        return runOnServer("teleport players to " + x + " " + y + " " + z, server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.teleportTo(x, y, z);
            }
        });
    }

    /** Points the local player's view at a world position. */
    public GameTest lookAt(double x, double y, double z) {
        return run("look at " + x + " " + y + " " + z, ctx -> {
            LocalPlayer player = ctx.player();
            Vec3 eye = player.getEyePosition();
            double dx = x - eye.x;
            double dy = y - eye.y;
            double dz = z - eye.z;
            double horizontal = Math.sqrt(dx * dx + dz * dz);
            player.setYRot((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
            player.setXRot((float) -Math.toDegrees(Math.atan2(dy, horizontal)));
        });
    }

    /** Sends a chat message, or a command when the text starts with {@code /}. */
    public GameTest chat(String message) {
        return run("chat: " + message, ctx -> {
            if (message.startsWith("/")) {
                ctx.player().connection.sendCommand(message.substring(1));
            } else {
                ctx.player().connection.sendChat(message);
            }
        });
    }

    /** Selects the first empty hotbar slot, so no held item alters the frame under test. */
    public GameTest selectEmptyHand() {
        return run("select an empty hotbar slot", ctx -> {
            net.minecraft.world.entity.player.Inventory inventory = ctx.player().getInventory();
            for (int slot = 0; slot < 9; slot++) {
                if (inventory.getItem(slot).isEmpty()) {
                    inventory.setSelectedSlot(slot);
                    return;
                }
            }
        });
    }

    public GameTest closeScreen() {
        return run("close screen", ctx -> ctx.client().setScreen(null));
    }

    /**
     * Saves a screenshot of the frame rendered with everything the previous steps set up: waits a
     * couple of ticks so a fresh frame exists, then completes only once the PNG is on disk.
     */
    public GameTest screenshot(String label) {
        int[] ticks = new int[1];
        boolean[] requested = new boolean[1];
        boolean[] saved = new boolean[1];
        return step("screenshot '" + label + "'", 200, ctx -> {
            if (++ticks[0] < 3) {
                return false;
            }
            if (!requested[0]) {
                requested[0] = true;
                ctx.saveScreenshot(label, () -> saved[0] = true);
            }
            return saved[0];
        });
    }
}
