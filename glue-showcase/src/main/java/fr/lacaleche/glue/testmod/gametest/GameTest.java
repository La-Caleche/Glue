package fr.lacaleche.glue.testmod.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
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
 * {@code GameTest.create("x").waitForWorld().give(Items.GLOWSTONE).screenshot("hud")} &mdash; each
 * step either completes, times out, or throws; the runner records the outcome per step and writes
 * a report next to the screenshots when the run ends.
 *
 * <p>A step is a {@link StepTick} polled every tick until it returns {@code true}. One-shot actions
 * ({@link #run}, {@link #give}) complete on their first tick; waits ({@link #waitUntil},
 * {@link #enableShaders}) poll until their condition holds. Anything not covered by a helper goes
 * through {@link #run} (client thread) / {@link #runOnServer} (server thread) or the raw
 * {@link #step} escape hatch.</p>
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

    /** 30 s at 20 TPS: generous for anything that is not world loading or shader compilation. */
    private static final int DEFAULT_TIMEOUT = 600;
    /** World join and Iris pack (re)compilation both legitimately take tens of seconds. */
    private static final int LONG_TIMEOUT = 2400;

    private final String name;
    private final List<Step> steps = new ArrayList<>();

    private GameTest(String name) {
        this.name = name;
    }

    public static GameTest create(String name) {
        return new GameTest(name);
    }

    public String name() {
        return name;
    }

    public List<Step> steps() {
        return steps;
    }

    /** Raw step: full control over description, timeout and per-tick logic. */
    public GameTest step(String description, int timeoutTicks, StepTick tick) {
        steps.add(new Step(description, timeoutTicks, tick));
        return this;
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

    /** Waits for level + player + no loading screen, then a settling delay past the join haze. */
    public GameTest waitForWorld() {
        waitUntil("world and player are loaded",
                ctx -> ctx.client().level != null && ctx.client().player != null
                        && ctx.client().screen == null,
                LONG_TIMEOUT);
        return waitTicks(40);
    }

    /** Fails the test immediately if the check does not hold. */
    public GameTest expect(String description, Predicate<TestContext> check) {
        return step("expect: " + description, DEFAULT_TIMEOUT, ctx -> {
            if (!check.test(ctx)) throw new AssertionError("expectation failed: " + description);
            return true;
        });
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

    /** Selects the first empty hotbar slot, so no held item (e.g. a picked-up glowstone) emits a
     *  shaderpack handheld light into the frame. Vanilla syncs the change on the next tick. */
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

    public GameTest openInventory() {
        return run("open inventory screen",
                ctx -> ctx.client().setScreen(new InventoryScreen(ctx.player())));
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
            if (++ticks[0] < 3) return false;
            if (!requested[0]) {
                requested[0] = true;
                ctx.saveScreenshot(label, () -> saved[0] = true);
            }
            return saved[0];
        });
    }

    /** Shows or hides the in-world light shape preview (the debug dock's wireframes) without the
     *  dock: with it on, a screenshot proves where every active light IS, independently of whether
     *  the lighting pipeline renders it. */
    public GameTest lightPreview(boolean shown) {
        return run((shown ? "show" : "hide") + " light shape preview",
                ctx -> fr.lacaleche.glue.testmod.render.LightShapePreviewRenderer.forced = shown);
    }

    /** Turns the configured Iris shaderpack on and waits until it is actually in use. */
    public GameTest enableShaders() {
        return setShaders(true);
    }

    /** Turns Iris shaders off (back to the vanilla/Sodium pipeline) and waits for the switch. */
    public GameTest disableShaders() {
        return setShaders(false);
    }

    private GameTest setShaders(boolean enabled) {
        boolean[] applied = new boolean[1];
        step((enabled ? "enable" : "disable") + " Iris shaders", LONG_TIMEOUT, ctx -> {
            if (!IrisHooks.loaded()) throw new IllegalStateException("Iris is not loaded");
            if (!applied[0]) {
                applied[0] = true;
                if (IrisHooks.shaderPackInUse() == enabled) return true;
                IrisHooks.setShadersEnabled(enabled);
            }
            return IrisHooks.shaderPackInUse() == enabled;
        });
        return waitTicks(10);
    }
}
