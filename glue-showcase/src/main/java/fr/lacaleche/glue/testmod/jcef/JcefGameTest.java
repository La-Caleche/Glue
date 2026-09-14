package fr.lacaleche.glue.testmod.jcef;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.testmod.gametest.RealInput;
import fr.lacaleche.jcef.CefScreen;
import fr.lacaleche.jcef.CefSurface;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.Cursor;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

final class JcefGameTest {

    private CefScreen screen;
    private CefSurface closing;
    private int commands;
    private int probe;
    private long frames;

    static void register() {
        GameTests.register("glue-test:jcef", () -> new JcefGameTest().build());
        GameTests.register("glue-test:jcef-sites", () -> new JcefGameTest().sites());
    }

    private GameTest build() {
        GameTest test = GameTest.create("glue-test:jcef").waitForWorld()
                .run("open the native Chromium React demo through F6", ctx -> {
                    ctx.client().setScreen(null);
                    this.commands = JcefDemo.receivedCommands();
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_F6);
                })
                .waitUntil("CEF produces the local page", ctx -> {
                    if (!(ctx.client().screen instanceof CefScreen browser)) return false;
                    this.screen = browser;
                    return this.ready();
                }, GameTest.LONG_TIMEOUT * 5)
                .waitTicks(20).screenshot("chromium-react");
        this.inspect(test, "hover a Chromium button through the host mouse handler", rect("increment"), box -> this.movePointer(box));
        test.waitUntil("Chromium hand cursor reaches GLFW", ctx ->
                        this.screen.surface().cursorType() == Cursor.HAND_CURSOR
                                && this.screen.appliedCursorShape() == GLFW.GLFW_POINTING_HAND_CURSOR)
                .run("move from web content into the native toolbar", ctx -> RealInput.moveToFramebuffer(ctx.client(), 4, 4))
                .waitUntil("native toolbar restores the default cursor", ctx -> this.screen.appliedCursorShape() == GLFW.GLFW_ARROW_CURSOR);
        this.inspect(test, "hover the web text editor", rect("callsign"), box -> this.movePointer(box));
        test.waitUntil("Chromium text cursor reaches GLFW", ctx ->
                this.screen.surface().cursorType() == Cursor.TEXT_CURSOR
                        && this.screen.appliedCursorShape() == GLFW.GLFW_IBEAM_CURSOR);
        this.inspect(test, "change the CSS cursor without moving the pointer",
                "(()=>{document.getElementById('callsign').style.cursor='crosshair';return {};})()", ignored -> {});
        test.waitUntil("asynchronous cursor updates reach the host", ctx -> this.screen.appliedCursorShape() == GLFW.GLFW_CROSSHAIR_CURSOR);
        this.inspect(test, "restore the editor cursor", "(()=>{document.getElementById('callsign').style.removeProperty('cursor');return {};})()", ignored -> {});
        test.waitUntil("the cached text cursor is reusable", ctx -> this.screen.appliedCursorShape() == GLFW.GLFW_IBEAM_CURSOR);
        this.inspect(test, "click the native Chromium button", rect("increment"), box -> this.click(box, 0.5));
        test.waitUntil("native page sends a command to Java", ctx -> JcefDemo.receivedCommands() > this.commands);
        this.inspect(test, "native click updates React", "demoSnapshot()", snapshot -> require(snapshot.get("count").getAsInt() == 1, "Click count"));
        this.inspect(test, "focus the native editor and type Unicode", rect("callsign"), box -> {
            this.click(box, 0.5);
            this.screen.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            this.screen.keyReleased(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            for (char character : "Étoile".toCharArray()) this.screen.charTyped(character, 0);
        });
        test.waitTicks(10);
        this.inspect(test, "React observes native text editing", "demoSnapshot()", snapshot -> require(snapshot.get("name").getAsString().equals("Étoile"), "Controlled Unicode field: " + snapshot));
        this.inspect(test, "drag the Chromium range control", rect("power"), box -> {
            double y = this.y(box);
            double from = this.x(box, 0.25);
            double to = this.x(box, 0.8);
            this.screen.mouseClicked(from, y, 0);
            this.screen.mouseDragged(to, y, 0, to - from, 0);
            this.screen.mouseReleased(to, y, 0);
        });
        test.waitTicks(10);
        this.inspect(test, "native range control changes", "demoSnapshot()", snapshot -> require(Math.abs(snapshot.get("power").getAsInt() - 80) <= 3, "Native range value: " + snapshot));
        test.screenshot("chromium-input");
        this.inspect(test, "open the CEF select popup", rect("effect"), box -> this.click(box, 0.5));
        test.waitTicks(5).screenshot("chromium-select")
                .run("choose the next native select option", ctx -> {
                    this.screen.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                    this.screen.keyReleased(GLFW.GLFW_KEY_DOWN, 0, 0);
                    this.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    this.screen.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                }).waitTicks(5);
        this.inspect(test, "native select retains its value", "demoSnapshot()", snapshot -> require(snapshot.get("effect").getAsString().equals("Ocean"), "Select value: " + snapshot));
        this.inspect(test, "start CSS animation", "(()=>{document.getElementById('animate').click();return {};})()", ignored -> this.frames = this.screen.surface().uploadedFrames());
        test.waitTicks(30).expect("Chromium keeps producing animated frames", ctx -> this.screen.surface().uploadedFrames() - this.frames > 10)
                .run("measure a pixel-correlated round trip", ctx -> this.probe = this.screen.surface().probe())
                .waitUntil("probe reaches the uploaded texture", ctx -> this.probe != 0 && this.screen.surface().completedProbe() == this.probe)
                .waitTicks(25).run("record GPU-swizzle metrics", ctx -> ctx.log("GPU BGRA: " + this.screen.surface().metrics()))
                .screenshot("chromium-gpu-swizzle")
                .run("switch to the CPU-conversion comparison", ctx -> this.screen.surface().setUploadMode(CefSurface.UploadMode.CPU_RGBA))
                .waitTicks(30)
                .run("probe CPU-swizzle presentation", ctx -> this.probe = this.screen.surface().probe())
                .waitUntil("CPU probe reaches texture", ctx -> this.screen.surface().completedProbe() == this.probe)
                .waitTicks(25).run("record CPU-swizzle metrics", ctx -> ctx.log("CPU RGBA: " + this.screen.surface().metrics()))
                .screenshot("chromium-cpu-swizzle")
                .run("resize the native viewport", ctx -> this.screen.surface().resize(800, 600))
                .waitTicks(15);
        this.inspect(test, "CEF viewport follows resize", "({width:innerWidth,height:innerHeight})", size -> require(size.get("width").getAsInt() == 800 && size.get("height").getAsInt() == 600, "Viewport size"));
        test.run("close the first native browser", ctx -> { this.closing = this.screen.surface(); this.screen.onClose(); })
                .waitUntil("CEF acknowledges native close", ctx -> this.closing.stopped().isDone())
                .run("open the read-only Chromium HUD", ctx -> JcefDemo.openHud(ctx.client()))
                .waitUntil("native HUD paints", ctx -> JcefDemo.hud().uploadedFrames() > 0)
                .waitTicks(15).screenshot("chromium-hud")
                .expect("HUD leaves gameplay input intact", ctx -> ctx.client().screen == null && ctx.client().mouseHandler.isMouseGrabbed())
                .run("release HUD resources", ctx -> { this.closing = JcefDemo.hud(); JcefDemo.closeHud(); })
                .waitUntil("HUD native browser closes", ctx -> this.closing.stopped().isDone())
                .run("reopen Chromium without restarting CEF", ctx -> this.screen = JcefDemo.open(ctx.client(), false))
                .waitUntil("reopened page paints", ctx -> this.ready()).waitTicks(10);
        this.inspect(test, "reopened React state is fresh", "demoSnapshot()", snapshot -> require(snapshot.get("count").getAsInt() == 0, "Reopened state"));
        this.inspect(test, "hover a button after recreating native cursors", rect("increment"), box -> this.movePointer(box));
        return test.waitUntil("reopened screen owns a new hand cursor", ctx -> this.screen.appliedCursorShape() == GLFW.GLFW_POINTING_HAND_CURSOR)
                .run("close while a custom cursor is active", ctx -> { this.closing = this.screen.surface(); this.screen.onClose(); })
                .expect("closing releases the custom cursor", ctx -> this.screen.appliedCursorShape() == GLFW.GLFW_ARROW_CURSOR)
                .waitUntil("all native browsers are closed", ctx -> this.closing.stopped().isDone());
    }

    private GameTest sites() {
        GameTest test = GameTest.create("glue-test:jcef-sites").waitForWorld()
                .run("open La Calèche in Chromium", ctx -> { ctx.client().setScreen(null); this.screen = JcefDemo.open(ctx.client(), true); })
                .waitUntil("La Calèche loads", ctx -> this.ready(), GameTest.LONG_TIMEOUT * 5)
                .waitTicks(60)
                .run("enable telemetry and pixel probe", ctx -> { this.screen.keyPressed(GLFW.GLFW_KEY_F3, 0, 0); this.probe = this.screen.surface().probe(); })
                .waitUntil("site probe is presented", ctx -> this.probe > 0 && this.screen.surface().completedProbe() == this.probe)
                .waitTicks(40).screenshot("lacaleche-chromium")
                .run("record real-site delivery metrics", ctx -> ctx.log("La Calèche: " + this.screen.surface().metrics()))
                .run("render La Calèche at the reported 2560x1178 resolution", ctx -> this.screen.surface().resize(2560, 1178))
                .waitTicks(100)
                .run("probe the larger surface", ctx -> this.probe = this.screen.surface().probe())
                .waitUntil("large-surface probe is presented", ctx -> this.screen.surface().completedProbe() == this.probe)
                .waitTicks(40)
                .run("record large-surface metrics", ctx -> ctx.log("La Calèche 2560x1178: " + this.screen.surface().metrics()))
                .run("compare 30 FPS native pacing", ctx -> this.screen.surface().setFpsLimit(30))
                .waitTicks(60)
                .run("probe the paced large surface", ctx -> this.probe = this.screen.surface().probe())
                .waitUntil("paced probe is presented", ctx -> this.screen.surface().completedProbe() == this.probe)
                .waitTicks(40)
                .run("record paced metrics", ctx -> ctx.log("La Calèche 2560x1178 cap30: " + this.screen.surface().metrics()));
        test.step("save the native-resolution Chromium image", GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<Void> saved;
            @Override
            public boolean tick(TestContext ctx) {
                if (this.saved == null) {
                    Path destination = ctx.client().gameDirectory.toPath().resolve("screenshots/gametest/glue-test_jcef-sites/native-lacaleche-2560.png");
                    this.saved = screen.surface().screenshot().thenAcceptAsync(image -> {
                        try {
                            if (!ImageIO.write(image, "png", destination.toFile())) throw new IOException("No PNG writer");
                        } catch (IOException exception) { throw new IllegalStateException(exception); }
                    });
                }
                if (!this.saved.isDone()) return false;
                this.saved.join();
                return true;
            }
        });
        test.run("navigate to Google", ctx -> {
                    this.screen.surface().resize(this.screen.webWidth(), this.screen.webHeight());
                    this.screen.surface().setFpsLimit(60);
                    this.screen.navigate("https://www.google.com/");
                })
                .waitUntil("Google paints", ctx -> this.ready() && this.screen.surface().url().contains("google"))
                .waitTicks(40).screenshot("google-chromium");
        this.inspect(test, "dismiss Google consent if offered", "(()=>{const e=document.getElementById('W0wltc');if(!e)return {present:false};e.scrollIntoView({block:'center'});return {present:true,...e.getBoundingClientRect().toJSON()};})()", box -> {
            if (box.get("present").getAsBoolean()) this.click(box, 0.5);
        });
        test.waitTicks(40);
        this.inspect(test, "type a Google search using native input", "(()=>{const e=document.querySelector('textarea[name=q],input[name=q]');if(!e)throw new Error('Google search field missing');e.scrollIntoView({block:'center'});return e.getBoundingClientRect().toJSON();})()", box -> {
            this.click(box, 0.5);
            for (char character : "chromium embedded framework".toCharArray()) this.screen.charTyped(character, 0);
            this.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            this.screen.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
        });
        test.waitUntil("Google responds to the submitted search", ctx -> {
                    String path = URI.create(this.screen.surface().url()).getPath();
                    return this.ready() && (path.equals("/search") || path.startsWith("/sorry"));
                }, GameTest.LONG_TIMEOUT)
                .run("record the Google response type", ctx -> ctx.log(URI.create(this.screen.surface().url()).getPath().startsWith("/sorry")
                        ? "Google returned a site challenge; search results are NOT validated."
                        : "Google returned a search-results URL."))
                .waitTicks(40).screenshot("google-search-native-input")
                .run("navigate to YouTube", ctx -> this.screen.navigate("https://www.youtube.com/"))
                .waitUntil("YouTube loads", ctx -> this.ready() && this.screen.surface().url().contains("youtube"), GameTest.LONG_TIMEOUT)
                .waitTicks(100).screenshot("youtube-chromium")
                .run("close the browser", ctx -> { this.closing = this.screen.surface(); this.screen.onClose(); })
                .waitUntil("native browser closes", ctx -> this.closing.stopped().isDone());
        return test;
    }

    private boolean ready() {
        if (!this.screen.surface().error().isEmpty()) throw new AssertionError(this.screen.surface().error());
        return this.screen.surface().isReady() && !this.screen.surface().isLoading() && this.screen.surface().uploadedFrames() > 0;
    }

    private void inspect(GameTest test, String label, String expression, Consumer<JsonObject> assertion) {
        test.step(label, GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<JsonElement> result;
            @Override
            public boolean tick(TestContext context) {
                if (this.result == null) this.result = screen.surface().evaluate(expression);
                if (!this.result.isDone()) return false;
                assertion.accept(this.result.join().getAsJsonObject());
                return true;
            }
        });
    }

    private void click(JsonObject rect, double ratio) {
        double x = this.x(rect, ratio);
        double y = this.y(rect);
        this.screen.mouseMoved(x, y);
        this.screen.mouseClicked(x, y, 0);
        this.screen.mouseReleased(x, y, 0);
    }

    private void movePointer(JsonObject rect) {
        Minecraft client = Minecraft.getInstance();
        double x = this.x(rect, 0.5) * client.getWindow().getWidth() / this.screen.width;
        double y = this.y(rect) * client.getWindow().getHeight() / this.screen.height;
        // MouseHandler uses the first position after releaseMouse only to reset its origin.
        // A second, distinct position produces the movement the screen actually receives.
        RealInput.moveToFramebuffer(client, x - 1, y);
        RealInput.moveToFramebuffer(client, x, y);
    }

    private double x(JsonObject rect, double ratio) { return (rect.get("x").getAsDouble() + rect.get("width").getAsDouble() * ratio) * this.screen.width / this.screen.webWidth(); }
    private double y(JsonObject rect) { return this.screen.viewY() + (rect.get("y").getAsDouble() + rect.get("height").getAsDouble() / 2) * this.screen.viewHeight() / this.screen.webHeight(); }
    private static String rect(String id) { return "(()=>{const e=document.getElementById('" + id + "');e.scrollIntoView({block:'center'});return e.getBoundingClientRect().toJSON();})()"; }
    private static void require(boolean condition, String description) { if (!condition) throw new AssertionError(description); }
}
