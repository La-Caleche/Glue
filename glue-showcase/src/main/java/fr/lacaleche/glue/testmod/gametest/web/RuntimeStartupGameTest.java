package fr.lacaleche.glue.testmod.gametest.web;

import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.web.WebSurface;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Exercises the real global GUI seam; only the progress snapshot is substituted by this fixture. */
final class RuntimeStartupGameTest {

    private ProgressFixture fixture;
    private Screen previousScreen;
    private Overlay previousOverlay;
    private boolean previousHideGui;
    private Screen menu;
    private Screen inventory;

    static void register() {
        GameTests.register("glue-test:web-startup", () -> new RuntimeStartupGameTest().build());
    }

    private GameTest build() {
        return GameTest.create("glue-test:web-startup").waitForWorld()
                .waitUntil("runtime is ready before creating any web surface",
                        ctx -> WebSurface.runtimeStatus().startsWith("Chromium "), GameTest.LONG_TIMEOUT * 5)
                .run("retain the original startup and screen state", ctx -> {
                    this.fixture = new ProgressFixture();
                    this.previousScreen = ctx.client().screen;
                    this.previousOverlay = ctx.client().getOverlay();
                    this.previousHideGui = ctx.client().options.hideGui;
                    ClientLifecycleEvents.CLIENT_STOPPING.register(client -> this.restore(client));
                    GLFW.glfwFocusWindow(ctx.client().getWindow().getWindow());
                })
                .waitUntil("client window has OS focus before testing gameplay input", ctx -> ctx.client().isWindowActive())
                .run("show download progress on the title screen", ctx -> {
                    this.fixture.show("DOWNLOADING", 47);
                    ctx.client().options.hideGui = false;
                    this.menu = new TitleScreen();
                    ctx.client().setScreen(this.menu);
                }).waitTicks(3).screenshot("startup-menu")
                .expect("progress leaves the menu as the input owner", ctx -> ctx.client().screen == this.menu)
                .run("show extraction progress over an inventory", ctx -> {
                    this.fixture.show("EXTRACTING", 68);
                    this.inventory = new InventoryScreen(ctx.player());
                    ctx.client().setScreen(this.inventory);
                }).waitTicks(3).screenshot("startup-inventory")
                // Creative players get the creative inventory in place of the survival one.
                .expect("progress leaves inventory input intact", ctx -> ctx.client().screen == this.inventory
                        || ctx.client().screen instanceof CreativeModeInventoryScreen)
                .run("show indeterminate progress during gameplay", ctx -> {
                    this.fixture.show("INITIALIZING", -1);
                    ctx.client().setScreen(null);
                }).waitTicks(3).screenshot("startup-gameplay")
                .expect("progress does not capture gameplay input",
                        ctx -> ctx.client().screen == null && ctx.client().mouseHandler.isMouseGrabbed())
                .run("hide the game HUD while startup remains visible", ctx -> ctx.client().options.hideGui = true)
                .waitTicks(3).screenshot("startup-hidden-hud")
                .run("show progress over a loading overlay", ctx -> {
                    ctx.client().options.hideGui = false;
                    ctx.client().setOverlay(new LoadingFixture());
                }).waitTicks(3).screenshot("startup-loading-overlay")
                .run("show a nonblocking failure", ctx -> {
                    ctx.client().setOverlay(null);
                    this.fixture.show("FAILED", -1);
                }).waitTicks(3).screenshot("startup-failure")
                .run("show readiness", ctx -> this.fixture.show("READY", 100))
                .waitTicks(3).screenshot("startup-ready")
                .waitTicks(45).screenshot("startup-dismissed")
                .run("restore the real startup snapshot and host state", ctx -> this.restore(ctx.client()));
    }

    private void restore(Minecraft client) {
        if (this.fixture == null) return;
        this.fixture.restore();
        this.fixture = null;
        client.options.hideGui = this.previousHideGui;
        client.setOverlay(this.previousOverlay);
        client.setScreen(this.previousScreen);
    }

    private static final class LoadingFixture extends Overlay {

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), 0xFF253448);
        }
    }

    /** Reflection is confined to development code; the library has no fake-progress or timing switch. */
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
