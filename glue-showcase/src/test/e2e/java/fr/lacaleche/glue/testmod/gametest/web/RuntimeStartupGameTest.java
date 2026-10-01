package fr.lacaleche.glue.testmod.gametest.web;

import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.web.WebSurface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Duration;

/** Exercises the real global GUI seam; only the progress snapshot is substituted by this fixture. */
@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.CompareObjectsWithEquals"})
@ClientTestSpec("web-startup")
public final class RuntimeStartupGameTest extends WorldClientTest {

    @Override
    protected void test() {
        this.waitUntil("runtime preloaded", client -> WebSurface.runtimeStatus().startsWith("Chromium "));
        ProgressFixture fixture = this.context.computeOnClient(client -> new ProgressFixture());
        Screen previousScreen = this.context.computeOnClient(client -> client.screen);
        Overlay previousOverlay = this.context.computeOnClient(client -> client.getOverlay());
        boolean previousHideGui = this.context.computeOnClient(client -> client.options.hideGui);
        try {
            Screen menu = this.context.computeOnClient(client -> {
                fixture.show("DOWNLOADING", 47);
                client.options.hideGui = false;
                Screen screen = new TitleScreen();
                client.setScreen(screen);
                return screen;
            });
            this.screenshot("startup-menu");
            this.game.expect("menu input owner", client -> require(client.screen == menu, "Startup overlay replaced menu"));
            Screen inventory = this.context.computeOnClient(client -> {
                fixture.show("EXTRACTING", 68);
                Screen screen = new InventoryScreen(client.player);
                client.setScreen(screen);
                return screen;
            });
            this.screenshot("startup-inventory");
            this.game.expect("inventory input owner", client -> require(client.screen == inventory, "Startup overlay replaced inventory"));
            this.context.runOnClient(client -> {
                fixture.show("INITIALIZING", -1);
                client.setScreen(null);
            });
            this.screenshot("startup-gameplay");
            this.game.expect("gameplay input", client -> require(client.screen == null && client.mouseHandler.isMouseGrabbed(), "Startup captured input"));
            this.context.runOnClient(client -> client.options.hideGui = true);
            this.screenshot("startup-hidden-hud");
            this.context.runOnClient(client -> {
                client.options.hideGui = false;
                client.setOverlay(new LoadingFixture());
            });
            this.screenshot("startup-loading-overlay");
            this.context.runOnClient(client -> {
                client.setOverlay(null);
                fixture.show("FAILED", -1);
            });
            this.screenshot("startup-failure");
            this.context.runOnClient(client -> fixture.show("READY", 100));
            this.screenshot("startup-ready");
            // Dismissal uses real elapsed time, independently of Fabric's controlled ticks.
            long readyAt = System.nanoTime();
            this.game.waitUntil("ready indicator dismissal", client -> System.nanoTime() - readyAt >= Duration.ofSeconds(3).toNanos(), Duration.ofSeconds(5));
            this.screenshot("startup-dismissed");
        } finally {
            this.context.runOnClient(client -> {
                fixture.restore();
                client.options.hideGui = previousHideGui;
                client.setOverlay(previousOverlay);
                client.setScreen(previousScreen);
            });
        }
    }

    private static final class LoadingFixture extends Overlay {

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF253448);
        }
    }

    /** Reflection is confined to development code; the library has no fake-progress or timing switch. */
    @SuppressWarnings("PMD.AvoidAccessibilityAlteration")
    private static final class ProgressFixture {

        private final Object progress;
        private final Field snapshotField;
        private final Constructor<?> snapshotConstructor;
        private final Class<?> stageType;
        private final Object original;
        private Object injected;

        ProgressFixture() {
            try {
                Class<?> runtime = Class.forName("fr.lacaleche.glue.web.internal.browser.CefRuntime");
                Field field = runtime.getDeclaredField("PROGRESS");
                field.setAccessible(true);
                this.progress = field.get(null);
                this.snapshotField = this.progress.getClass().getDeclaredField("snapshot");
                this.snapshotField.setAccessible(true);
                this.original = this.snapshotField.get(this.progress);
                this.stageType = Class.forName("fr.lacaleche.glue.web.internal.browser.RuntimeStartup$Stage");
                this.snapshotConstructor = this.original.getClass().getDeclaredConstructor(
                        this.stageType, int.class, String.class, long.class);
                this.snapshotConstructor.setAccessible(true);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Startup fixture could not access its view model", exception);
            }
        }

        void show(String name, int percent) {
            try {
                Field stageField = this.stageType.getDeclaredField(name);
                stageField.setAccessible(true);
                Object stage = stageField.get(null);
                this.injected = this.snapshotConstructor.newInstance(stage, percent, "Visual startup fixture", System.nanoTime());
                this.snapshotField.set(this.progress, this.injected);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not publish startup fixture", exception);
            }
        }

        void restore() {
            try {
                // Do not overwrite a real shutdown transition if the test is interrupted.
                if (this.snapshotField.get(this.progress) == this.injected) this.snapshotField.set(this.progress, this.original);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not restore startup fixture", exception);
            }
        }
    }
}
