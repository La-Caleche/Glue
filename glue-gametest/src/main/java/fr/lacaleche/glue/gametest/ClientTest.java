package fr.lacaleche.glue.gametest;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Sequential helpers used inside a Fabric client gametest's {@code runTest} method.
 * Fabric owns execution, ticking, input and world lifetime. Call these helpers on its test thread;
 * assertion callbacks run on the client thread and must be short and free of side effects.
 */
@Environment(EnvType.CLIENT)
@SuppressWarnings("PMD.TestClassWithoutTestCases") // Test support, not a JUnit test class.
public final class ClientTest {

    private final ClientGameTestContext context;

    public ClientTest(ClientGameTestContext context) {
        this.context = Objects.requireNonNull(context, "context");
    }

    /**
     * Waits for external work while letting Fabric advance the game. The future belongs to its
     * producer and is not cancelled on timeout. Unlike tick deadlines, this uses wall-clock time.
     */
    public <T> T await(String description, CompletableFuture<T> future, Duration timeout) {
        Objects.requireNonNull(future, "future");
        this.waitUntil(description, client -> future.isDone(), timeout);
        try {
            return future.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new IllegalStateException(description, cause == null ? exception : cause);
        }
    }

    /** Polls on the client thread while ticking, with a wall-clock deadline for external systems. */
    public void waitUntil(String description, Predicate<Minecraft> condition, Duration timeout) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        long started = System.nanoTime();
        long budget = timeout.toNanos();
        while (!this.context.computeOnClient(condition::test)) {
            if (System.nanoTime() - started >= budget) {
                throw new AssertionError("Timed out after " + timeout + ": " + description);
            }
            this.context.waitTick();
        }
    }

    /** Waits for actual world renders, including after synchronous resource/shader reloads. */
    public void waitForWorldFrames(int frames) {
        FrameSettle settle = new FrameSettle(frames);
        this.context.runOnClient(client -> {
            if (client.level == null) throw new IllegalStateException("World-frame waits require a loaded world");
            settle.settled(WorldFrames.count());
        });
        this.context.waitFor(client -> settle.settled(WorldFrames.count()),
                Math.max(ClientGameTestContext.DEFAULT_TIMEOUT, frames));
    }

    /** Moves in GUI coordinates through Fabric input, accounting for window/GUI scale. */
    public void movePointer(double guiX, double guiY) {
        PointerPosition position = this.context.computeOnClient(client -> new PointerPosition(
                guiX * client.getWindow().getScreenWidth() / client.getWindow().getGuiScaledWidth(),
                guiY * client.getWindow().getScreenHeight() / client.getWindow().getGuiScaledHeight()));
        this.context.getInput().setCursorPos(position.x + 1, position.y);
        this.context.getInput().setCursorPos(position.x, position.y);
    }

    /** Opens the survival inventory through its configured key binding, starting from gameplay. */
    public ContainerTest openInventory() {
        this.context.runOnClient(client -> {
            if (client.player == null || client.screen != null) {
                throw new IllegalStateException("Opening the inventory requires a player in gameplay");
            }
            if (client.player.hasInfiniteMaterials()) {
                throw new IllegalStateException("Opening the survival inventory requires a non-creative player");
            }
        });
        this.context.getInput().pressKey(options -> options.keyInventory);
        return this.container(InventoryScreen.class);
    }

    /** Waits for an open container and binds a handle to that screen instance. */
    public ContainerTest container(Class<? extends AbstractContainerScreen<?>> screenType) {
        Objects.requireNonNull(screenType, "screenType");
        this.expect("screen " + screenType.getSimpleName(), client -> {
            if (!screenType.isInstance(client.screen)) {
                throw new AssertionError("Expected " + screenType.getSimpleName() + ", got "
                        + (client.screen == null ? "gameplay" : client.screen.getClass().getSimpleName()));
            }
        });
        AbstractContainerScreen<?> screen = this.context.computeOnClient(client -> screenType.cast(client.screen));
        return new ContainerTest(this, screen);
    }

    /** Retries assertion failures with Fabric's default timeout. Other exceptions fail immediately. */
    public void expect(String description, Consumer<Minecraft> assertion) {
        this.expect(description, ClientGameTestContext.DEFAULT_TIMEOUT, assertion);
    }

    /**
     * Retries assertion failures while advancing Fabric ticks, retaining the last failure for diagnostics.
     * The timeout is in ticks, not wall-clock time; this cannot recover a blocked game thread.
     */
    public void expect(String description, int timeoutTicks, Consumer<Minecraft> assertion) {
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(assertion, "assertion");
        if (timeoutTicks <= 0) throw new IllegalArgumentException("timeoutTicks must be positive");

        AssertionProbe probe = new AssertionProbe(assertion);
        try {
            this.context.waitFor(probe, timeoutTicks);
        } catch (AssertionError timeout) {
            if (probe.failure == null) throw timeout;
            throw new AssertionError("Timed out after " + timeoutTicks + " ticks: " + description
                    + ". Last assertion: " + probe.failure.getMessage(), probe.failure);
        }
    }

    ClientGameTestContext context() {
        return this.context;
    }

    private static final class AssertionProbe implements Predicate<Minecraft> {

        private final Consumer<Minecraft> assertion;
        // Fabric's synchronous client hand-off publishes this to the waiting test thread.
        private AssertionError failure;

        private AssertionProbe(Consumer<Minecraft> assertion) {
            this.assertion = assertion;
        }

        @Override
        public boolean test(Minecraft client) {
            try {
                this.assertion.accept(client);
                return true;
            } catch (AssertionError failure) {
                this.failure = failure;
                return false;
            }
        }
    }

    private record PointerPosition(double x, double y) {
    }
}
