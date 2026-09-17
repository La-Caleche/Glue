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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
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
 * <p>Steps carry per-run closure state, so a {@code GameTest} instance supports one run. Register
 * through {@link GameTests#register(String, java.util.function.Supplier)} and the runner builds a
 * fresh instance &mdash; fresh closure state &mdash; each time it selects the test.</p>
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

    /**
     * Schedules an action on the integrated server thread and waits until it has executed. An action
     * that throws fails the step on the following tick, carrying its own exception into the report
     * instead of leaving the step to time out with nothing to show for it.
     *
     * <p>The action must be a short, non-blocking server mutation. A step timeout stops the runner
     * from waiting, but it cannot unwind work that has already wedged Minecraft's server thread: a
     * running task is past the point where cancelling a future reaches it, and interrupting that
     * thread is not something a test may do &mdash; client shutdown would then wait on the integrated
     * server indefinitely. Wait for conditions with polled client steps ({@link #waitUntil}) rather
     * than inside the action, and keep locks, latches, network calls and long I/O out of it.</p>
     */
    public GameTest runOnServer(String description, Consumer<MinecraftServer> action) {
        // Holds the server's own future: a plain flag written on the server thread and read on the
        // client tick has no happens-before edge between them, and says nothing when the action threw.
        AtomicReference<CompletableFuture<Void>> pending = new AtomicReference<>();
        return step(description, DEFAULT_TIMEOUT, ctx -> {
            CompletableFuture<Void> completion = pending.get();
            if (completion == null) {
                MinecraftServer server = ctx.server();
                completion = server.submit(() -> action.accept(server));
                pending.set(completion);
            }
            if (!completion.isDone()) return false;

            try {
                completion.join();
            } catch (CompletionException wrapped) {
                rethrowServerFailure(wrapped);
            }
            return true;
        });
    }

    /**
     * Fails the step with what the server action threw rather than with the executor's wrapper. Both
     * throwable families matter: an {@code AssertionError} raised by a check inside the action is
     * exactly the failure the report should name, and it is not an {@code Exception}. A wrapper
     * carrying no cause is rethrown as it stands, since there is nothing better to report.
     */
    static void rethrowServerFailure(CompletionException wrapped) throws Exception {
        Throwable cause = wrapped.getCause();
        if (cause instanceof Exception failure) throw failure;
        if (cause instanceof Error failure) throw failure;

        throw wrapped;
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
     * couple of ticks so a fresh frame exists, then completes only once the PNG is on disk. A capture
     * that fails to write fails the step: a run that stayed green while its evidence never landed
     * would be worse than one that reports the failure.
     */
    public GameTest screenshot(String label) {
        int[] ticks = new int[1];
        boolean[] requested = new boolean[1];
        // The outcome is published from Minecraft's screenshot I/O thread and read here on the client
        // thread; the atomic is what makes that hand-off visible rather than a hopeful plain write.
        AtomicReference<TestContext.ScreenshotOutcome> outcome = new AtomicReference<>();
        return step("screenshot '" + label + "'", 200, ctx -> {
            if (++ticks[0] < 3) {
                return false;
            }
            if (!requested[0]) {
                requested[0] = true;
                ctx.saveScreenshot(label, outcome::set);
            }

            TestContext.ScreenshotOutcome result = outcome.get();
            if (result == null) {
                return false;
            }
            if (!result.saved()) {
                throw new IllegalStateException("screenshot '" + label + "' was not saved: " + result.detail());
            }
            return true;
        });
    }
}
