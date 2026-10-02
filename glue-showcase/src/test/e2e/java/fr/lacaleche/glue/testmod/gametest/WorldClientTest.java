package fr.lacaleche.glue.testmod.gametest;

import fr.lacaleche.glue.gametest.ClientTest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.time.Duration;
import java.util.function.Predicate;

/** Shared disposable-world fixture; discovery and execution belong entirely to Fabric. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
public abstract class WorldClientTest implements FabricClientGameTest {

    protected ClientGameTestContext context;
    protected ClientTest game;
    protected TestSingleplayerContext world;

    @Override
    @SuppressWarnings("PMD.AvoidCatchingGenericException") // Fixture boundary: attach diagnostics and rethrow the original failure.
    public final void runTest(ClientGameTestContext context) {
        this.context = context;
        this.game = new ClientTest(context);
        try (TestSingleplayerContext world = context.worldBuilder().create()) {
            this.world = world;
            world.getServer().runOnServer(server -> {
                ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
                player.setGameMode(GameType.SURVIVAL);
                player.getAbilities().mayfly = true;
                player.getAbilities().flying = true;
                player.onUpdateAbilities();
            });
            world.getClientWorld().waitForChunksRender();
            context.getInput().resizeWindow(1280, 720);
            context.runOnClient(client -> {
                client.options.guiScale().set(2);
                client.resizeDisplay();
            });
            try {
                this.test();
            } catch (RuntimeException | Error failure) {
                try {
                    context.takeScreenshot(this.getClass().getSimpleName() + "-FAILED");
                } catch (RuntimeException | Error captureFailure) {
                    failure.addSuppressed(captureFailure);
                }
                throw failure;
            } finally {
                context.setScreen(() -> null);
            }
        }
    }

    protected abstract void test();

    protected void waitUntil(String description, Predicate<Minecraft> condition) {
        this.game.waitUntil(description, condition, Duration.ofSeconds(60));
    }

    protected void screenshot(String name) {
        this.context.waitTicks(3);
        this.context.takeScreenshot(name);
    }

    protected static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /**
     * The shapes enclose the same volume, give or take float rounding: a model's coordinates off the
     * binary grid land a few ulps apart once turned.
     */
    protected static void requireSame(String what, VoxelShape expected, VoxelShape actual) {
        double difference = volume(Shapes.join(expected, actual, BooleanOp.NOT_SAME));
        require(difference < 1e-6, what + " differs by " + difference + ": it is " + actual.toAabbs()
                + ", expected " + expected.toAabbs());
    }

    protected static double volume(VoxelShape shape) {
        double volume = 0;
        for (AABB box : shape.toAabbs()) volume += box.getXsize() * box.getYsize() * box.getZsize();
        return volume;
    }
}
