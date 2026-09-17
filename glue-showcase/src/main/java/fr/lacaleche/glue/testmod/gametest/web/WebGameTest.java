package fr.lacaleche.glue.testmod.gametest.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.gametest.RealInput;
import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.testmod.web.browser.BrowserScreen;
import fr.lacaleche.glue.testmod.web.inventory.InventoryPanel;
import fr.lacaleche.glue.testmod.web.lab.LabActions;
import fr.lacaleche.glue.testmod.web.waypoint.Waypoints;
import fr.lacaleche.glue.web.WebCursor;
import fr.lacaleche.glue.web.host.WebScreen;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.WebSurface;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Scripted Glue Web scenarios. Pointer and keyboard steps go through Minecraft's real input
 * handlers; page state is read back with script evaluation.
 */
public final class WebGameTest {

    private static final Origin SCREEN = () -> new int[] {0, 0};

    private WebScreen hub;
    private WebSurface hubPage;
    private WebScreen lab;
    private WebScreen waypoints;
    private WebSurface waypointsPage;
    private InventoryScreen inventory;
    private BrowserScreen browser;
    private WebSurface closing;
    private WebSurface shutdownSurface;
    private int increments;
    private int camps;
    private long frames;
    private volatile GameType previousMode;

    public static void register() {
        RuntimeStartupGameTest.register();
        GameTests.register("glue-test:web", () -> new WebGameTest().build());
        GameTests.register("glue-test:web-sites", () -> new WebGameTest().sites());
    }

    private GameTest build() {
        GameTest test = GameTest.create("glue-test:web").waitForWorld()
                .waitUntil("Chromium preloaded without opening a surface",
                        ctx -> WebSurface.runtimeStatus().startsWith("Chromium "), GameTest.LONG_TIMEOUT * 5)
                .waitUntil("the test player is alive and out of free fall", WebGameTest::isSettled)
                .run("close a surface while CEF acquisition is pending", ctx -> {
                    this.closing = WebSurface.builder(URI.create("about:blank")).size(32, 32).transparent(false).open();
                    this.closing.close();
                })
                .waitUntil("early close completes normally", ctx -> this.closing.stopped().isDone());
        this.hub(test);
        this.lab(test);
        this.layers(test);
        this.inventory(test);
        this.untrustedBrowser(test);
        return test.run("open an unhosted surface for module-owned shutdown", ctx -> {
                    this.shutdownSurface = WebSurface.builder(URI.create("about:blank")).size(32, 32).open();
                    this.shutdownSurface.stopped().thenRun(() -> Testmod.LOGGER.info("Module-owned web surface disposed"));
                })
                .waitUntil("the module-owned surface is ready", ctx -> this.shutdownSurface.isReady());
    }

    private void hub(GameTest test) {
        test.run("open the hub with F6", ctx -> {
                    ctx.client().setScreen(null);
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_F6);
                })
                .waitUntil("the hub page connects from its app origin", ctx -> {
                    if (!(ctx.client().screen instanceof WebScreen screen)) return false;
                    this.hub = screen;
                    this.hubPage = screen.surface().orElse(null);
                    return isPainted(this.hubPage, true);
                }, GameTest.LONG_TIMEOUT * 5)
                .expect("the hub is served from https://glue-showcase.glue/",
                        ctx -> this.hubPage.url().equals("https://glue-showcase.glue/index.html"))
                .waitTicks(10).screenshot("web-hub");
        this.inspect(test, "one CSS pixel is one GUI pixel", () -> this.hubPage,
                "({ratio: devicePixelRatio, width: innerWidth, height: innerHeight, renderer: document.documentElement.dataset.renderer})", page -> {
                    Minecraft client = Minecraft.getInstance();
                    require("react".equals(page.get("renderer").getAsString()), "The hub must mount its React component");
                    require(page.get("ratio").getAsDouble() == client.getWindow().getGuiScale(), "Device pixel ratio " + page);
                    require(page.get("width").getAsInt() == this.hub.width && page.get("height").getAsInt() == this.hub.height,
                            "Viewport " + page + " for a GUI of " + this.hub.width + "x" + this.hub.height);
                });
        this.click(test, "open the input lab from the hub", () -> this.hubPage, SCREEN, "[data-demo=lab]");
        test.waitUntil("the lab opens above the hub", ctx -> {
                    if (!(ctx.client().screen instanceof WebScreen screen) || screen == this.hub) return false;
                    this.lab = screen;
                    return screen.parent() == this.hub && isPainted(this.lab(), true);
                }, GameTest.LONG_TIMEOUT)
                .waitTicks(5)
                .expect("the hub page stays open beneath the lab", ctx -> !this.hubPage.isClosed());
    }

    private void lab(GameTest test) {
        this.hover(test, "hover a lab button", this::lab, SCREEN, "#increment");
        test.waitUntil("Chromium hand cursor reaches GLFW",
                ctx -> this.lab().cursor() == WebCursor.HAND && this.lab().appliedCursor() == WebCursor.HAND);
        this.hover(test, "hover the callsign field", this::lab, SCREEN, "#callsign");
        test.waitUntil("Chromium text cursor reaches GLFW", ctx -> this.lab().appliedCursor() == WebCursor.TEXT);
        this.inspect(test, "change the CSS cursor without moving the pointer", this::lab,
                "(()=>{document.getElementById('callsign').style.cursor='crosshair';return {};})()", ignored -> { });
        test.waitUntil("asynchronous cursor updates reach the host", ctx -> this.lab().appliedCursor() == WebCursor.CROSSHAIR);
        this.inspect(test, "restore the field cursor", this::lab,
                "(()=>{document.getElementById('callsign').style.removeProperty('cursor');return {};})()", ignored -> { });
        test.waitUntil("the cached text cursor is reusable", ctx -> this.lab().appliedCursor() == WebCursor.TEXT);
        this.inspect(test, "published game state reaches the vanilla page", this::lab, "labSnapshot()", lab -> {
            require("vanilla".equals(lab.get("renderer").getAsString()), "The input lab must stay framework-free");
            require(lab.get("connected").getAsBoolean() && lab.get("fps").getAsInt() > 0, "Lab state " + lab);
        });

        test.run("record page-to-Java calls", ctx -> this.increments = LabActions.increments());
        this.click(test, "click the count button", this::lab, SCREEN, "#increment");
        test.waitUntil("the page action runs in Java", ctx -> LabActions.increments() == this.increments + 1);
        this.waitForScript(test, "the action result returns to the page", this::lab,
                () -> "labSnapshot().count === " + (this.increments + 1));

        this.click(test, "focus the callsign field", this::lab, SCREEN, "#callsign");
        test.run("replace the callsign with Unicode text", ctx -> {
            this.lab.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            this.lab.keyReleased(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            for (char character : "Étoile".toCharArray()) this.lab.charTyped(character, 0);
        }).waitTicks(5);
        this.inspect(test, "native text editing reaches the page", this::lab, "labSnapshot()",
                lab -> require(lab.get("name").getAsString().equals("Étoile"), "Callsign " + lab));
        this.inspect(test, "drag the range control", this::lab, rect("#power"), box -> {
            double y = centerY(box, 0);
            double from = x(box, 0, 0.25);
            double to = x(box, 0, 0.8);
            this.lab.mouseClicked(from, y, 0);
            this.lab.mouseDragged(to, y, 0, to - from, 0);
            this.lab.mouseReleased(to, y, 0);
        });
        test.waitTicks(5);
        this.inspect(test, "the range follows the drag", this::lab, "labSnapshot()",
                lab -> require(Math.abs(lab.get("power").getAsInt() - 80) <= 3, "Range " + lab));
        test.screenshot("web-lab-input");

        this.inspect(test, "open the native select popup", this::lab, rect("#effect"),
                box -> this.pressAndRelease(this.lab::mouseClicked, this.lab::mouseReleased, x(box, 0, 0.5), centerY(box, 0)));
        test.waitUntil("the select popup is composited", ctx -> this.lab().hasPopup())
                .screenshot("web-lab-select")
                .run("choose the next option with the keyboard", ctx -> {
                    this.lab.keyPressed(GLFW.GLFW_KEY_DOWN, 0, 0);
                    this.lab.keyReleased(GLFW.GLFW_KEY_DOWN, 0, 0);
                    this.lab.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    this.lab.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                })
                .waitTicks(5);
        this.inspect(test, "the select keeps its value", this::lab, "labSnapshot()",
                lab -> require(lab.get("effect").getAsString().equals("Ocean"), "Effect " + lab));

        this.click(test, "greet through a Java action", this::lab, SCREEN, "#greet");
        this.waitForScript(test, "the action validates and answers", this::lab,
                () -> "labSnapshot().greeting === 'Hello from Chromium, Étoile'");
        this.click(test, "call an action that refuses", this::lab, SCREEN, "#refuse");
        this.waitForScript(test, "the refusal rejects the page promise", this::lab,
                () -> "labSnapshot().refusal === 'The lab refuses this request on purpose'");
        test.expect("Java events reach the connected page",
                ctx -> this.lab.emit("lab.ping", Map.of("message", "pong")));
        this.waitForScript(test, "the page listener received the event", this::lab,
                () -> "labSnapshot().lastEvent?.message === 'pong'");

        this.click(test, "start the CSS animation", this::lab, SCREEN, "#animate");
        test.run("count delivered frames", ctx -> this.frames = this.lab().uploadedFrames())
                .waitTicks(30)
                .expect("Chromium keeps producing animated frames", ctx -> this.lab().uploadedFrames() - this.frames > 10)
                .run("record delivery metrics", ctx -> ctx.log("Web delivery: " + this.lab().metrics()))
                .run("close the lab with Escape", ctx -> {
                    this.closing = this.lab();
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE);
                })
                .waitUntil("the lab returns to the same hub page", ctx -> ctx.client().screen == this.hub
                        && this.hub.surface().orElse(null) == this.hubPage && !this.hubPage.isClosed())
                .waitUntil("the lab page is disposed", ctx -> this.closing.stopped().isDone())
                .expect("the hub page is still connected", ctx -> this.hubPage.isConnected());
    }

    private void layers(GameTest test) {
        this.click(test, "show the vitals HUD from the hub", () -> this.hubPage, SCREEN, "[data-layer=hud]");
        this.click(test, "show the minimap from the hub", () -> this.hubPage, SCREEN, "[data-layer=minimap]");
        test.waitUntil("both HUD layers are enabled", ctx -> WebDemos.vitals().isEnabled() && WebDemos.minimap().isEnabled())
                // Survival shows the status bars and keeps the survival inventory; flight keeps the pose.
                .runOnServer("switch to flying survival", server -> {
                    ServerLifecycleEvents.SERVER_STOPPING.register(this::restoreGameMode);
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        this.previousMode = player.gameMode();
                        player.setGameMode(GameType.SURVIVAL);
                        player.getAbilities().mayfly = true;
                        player.getAbilities().flying = true;
                        player.onUpdateAbilities();
                    }
                })
                .waitUntil("the client is in survival", ctx -> ctx.client().gameMode.canHurtPlayer())
                .give(Items.DIAMOND_SWORD, Items.GRASS_BLOCK, Items.TORCH)
                .run("close the hub", ctx -> {
                    this.closing = this.hubPage;
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE);
                })
                .waitUntil("the hub page is disposed", ctx -> ctx.client().screen == null && this.closing.stopped().isDone())
                .run("select the third hotbar slot", ctx -> ctx.player().getInventory().setSelectedSlot(2))
                .waitUntil("the web HUD replaces the vanilla hotbar and bars",
                        ctx -> WebDemos.vitals().isShowing() && WebDemos.minimap().isShowing(), GameTest.LONG_TIMEOUT)
                .waitUntil("the HUD page reports its hotbar slots", ctx -> countSlots(WebDemos.vitals().surface().orElseThrow(), "item") >= 9)
                .waitUntil("the minimap reports its terrain slot", ctx -> countSlots(WebDemos.minimap().surface().orElseThrow(), "terrain") == 1)
                .waitTicks(20)
                .screenshot("web-hud");
        this.inspect(test, "the selected slot follows the game", () -> WebDemos.vitals().surface().orElseThrow(),
                "({selected: document.querySelector('.tile.is-selected')?.dataset.glueSlot})",
                page -> require("item:2".equals(page.get("selected").getAsString()), "Selected tile " + page));
        test.expect("HUD layers leave gameplay input intact",
                        ctx -> ctx.client().screen == null && ctx.client().mouseHandler.isMouseGrabbed())
                .run("show a toast", ctx -> WebDemos.toast("Scripted toast", "Shown above the inventory."))
                .waitUntil("the toast overlay is connected", ctx -> WebDemos.toasts().overlay().isShowing(), GameTest.LONG_TIMEOUT);
    }

    private void inventory(GameTest test) {
        test.run("open the survival inventory", ctx -> {
                    ctx.player().getRecipeBook().setOpen(RecipeBookType.CRAFTING, false);
                    this.inventory = new InventoryScreen(ctx.player());
                    ctx.client().setScreen(this.inventory);
                })
                .expect("the inventory panel fits beside the inventory", ctx -> InventoryPanel.widget().visible
                        || failure("Enlarge the window: the GUI is " + this.inventory.width + " pixels wide"))
                .waitUntil("the inventory panel connects", ctx -> isPainted(this.panel(), true), GameTest.LONG_TIMEOUT)
                .waitTicks(10)
                .screenshot("web-inventory-panel-and-toast")
                .run("count camps", ctx -> this.camps = Waypoints.all().size());
        this.click(test, "mark this spot from the panel", this::panel, this::panelOrigin, "#mark");
        test.waitUntil("the panel action adds a camp", ctx -> Waypoints.all().size() == this.camps + 1);
        this.click(test, "open waypoints from the panel", this::panel, this::panelOrigin, "#open");
        test.waitUntil("the waypoint screen opens over the inventory", ctx -> {
            if (!(ctx.client().screen instanceof WebScreen screen)) return false;
            this.waypoints = screen;
            this.waypointsPage = screen.surface().orElse(null);
            return screen.parent() == this.inventory && isPainted(this.waypointsPage, true);
        }, GameTest.LONG_TIMEOUT);

        this.click(test, "focus the waypoint name", () -> this.waypointsPage, SCREEN, "#name");
        test.run("type a waypoint name", ctx -> {
            for (char character : "Base".toCharArray()) this.waypoints.charTyped(character, 0);
        }).waitTicks(3);
        this.click(test, "add the waypoint", () -> this.waypointsPage, SCREEN, "#add");
        test.waitUntil("Java stores the new waypoint", ctx -> Waypoints.all().stream().anyMatch(waypoint -> waypoint.name().equals("Base")))
                .waitTicks(1);
        this.waitForScript(test, "the list shows the new waypoint", () -> this.waypointsPage,
                () -> "[...document.querySelectorAll('li .name')].some(name => name.textContent === 'Base')");
        test.waitTicks(5).screenshot("web-waypoints");

        String removeBase = "(()=>{const row=[...document.querySelectorAll('li')].find(li=>li.querySelector('.name').textContent==='Base');"
                + "if(!row)throw new Error('Base is not listed');return row.querySelector('.remove').getBoundingClientRect().toJSON();})()";
        this.clickExpression(test, "ask to remove the waypoint", () -> this.waypointsPage, SCREEN, removeBase);
        test.waitUntil("the removal dialog stacks above the waypoint screen", ctx -> ctx.client().screen instanceof WebScreen dialog
                        && dialog.parent() == this.waypoints && isPainted(dialog.surface().orElse(null), true), GameTest.LONG_TIMEOUT)
                .waitTicks(5)
                .expect("the waypoint page and the panel stay open beneath the dialog",
                        ctx -> !this.waypointsPage.isClosed() && !this.panel().isClosed())
                .screenshot("web-confirm-stacked");
        this.click(test, "confirm the removal", this::dialog, SCREEN, "#remove");
        test.waitUntil("the dialog closes onto the same waypoint page", ctx -> ctx.client().screen == this.waypoints
                        && this.waypoints.surface().orElse(null) == this.waypointsPage)
                .expect("Java removed the waypoint", ctx -> Waypoints.all().stream().noneMatch(waypoint -> waypoint.name().equals("Base")))
                .run("return to the inventory", ctx -> RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE))
                .waitUntil("the waypoint page is disposed", ctx -> ctx.client().screen == this.inventory
                        && this.waypointsPage.stopped().isDone())
                .expect("the panel kept its page through the stack", ctx -> isPainted(this.panel(), true))
                .run("close the inventory", ctx -> {
                    this.closing = this.panel();
                    RealInput.tap(ctx.client(), GLFW.GLFW_KEY_ESCAPE);
                })
                .waitUntil("the panel page closes with its screen", ctx -> this.closing.stopped().isDone())
                .run("hide both HUD layers", ctx -> {
                    this.closing = WebDemos.vitals().surface().orElseThrow();
                    WebDemos.vitals().setEnabled(false);
                    WebDemos.minimap().setEnabled(false);
                })
                .expect("disabling restores the vanilla HUD", ctx -> !WebDemos.vitals().isShowing()
                        && WebDemos.vitals().surface().isEmpty() && WebDemos.minimap().surface().isEmpty())
                .waitUntil("the HUD page is disposed", ctx -> this.closing.stopped().isDone())
                .waitTicks(5)
                .screenshot("web-vanilla-hud-restored")
                .runOnServer("restore the game mode", this::restoreGameMode);
    }

    private void untrustedBrowser(GameTest test) {
        test.run("open the lab in the browser screen", ctx -> this.browser = BrowserScreen.open(WebDemos.app().page("lab.html").toString()))
                .waitUntil("the untrusted lab page paints", ctx -> isPainted(this.browserPage(), false), GameTest.LONG_TIMEOUT);
        this.inspect(test, "the browser grants no bridge to the page", this::browserPage,
                "(async()=>{await new Promise(r=>setTimeout(r,200));return {bridge: document.documentElement.dataset.bridge};})()",
                page -> require("offline".equals(page.get("bridge").getAsString()), "Bridge " + page));
        test.expect("the page stays disconnected", ctx -> !this.browserPage().isConnected());
        this.hover(test, "hover a page button in the widget", this::browserPage, () -> new int[] {0, BrowserScreen.PAGE_TOP}, "#animate");
        test.waitUntil("the widget owns the hand cursor", ctx -> this.browserPage().appliedCursor() == WebCursor.HAND)
                .run("move into the vanilla toolbar", ctx -> {
                    RealInput.moveToFramebuffer(ctx.client(), 6, 6);
                    RealInput.moveToFramebuffer(ctx.client(), 4, 4);
                })
                .waitUntil("the toolbar restores the default cursor", ctx -> this.browserPage().appliedCursor() == WebCursor.ARROW)
                .screenshot("web-browser-untrusted")
                .run("close the browser", ctx -> {
                    this.closing = this.browserPage();
                    this.browser.onClose();
                })
                .waitUntil("the browser page is disposed", ctx -> this.closing.stopped().isDone());
    }

    private GameTest sites() {
        GameTest test = GameTest.create("glue-test:web-sites").waitForWorld()
                .run("open La Calèche in the browser", ctx -> {
                    ctx.client().setScreen(null);
                    this.browser = BrowserScreen.open(BrowserScreen.HOME);
                })
                .waitUntil("La Calèche loads", ctx -> isPainted(this.browserPage(), false), GameTest.LONG_TIMEOUT * 5)
                .waitTicks(60)
                .run("show detailed metrics", ctx -> this.browser.keyPressed(GLFW.GLFW_KEY_F3, 0, 0))
                .waitTicks(40).screenshot("lacaleche-chromium")
                .run("record real-site delivery metrics", ctx -> ctx.log("La Calèche: " + this.browserPage().metrics()))
                .run("compare 30 FPS pacing", ctx -> this.browserPage().setFpsLimit(30))
                .waitTicks(100)
                .run("record paced metrics", ctx -> ctx.log("La Calèche cap30: " + this.browserPage().metrics()))
                .run("restore 60 FPS pacing", ctx -> this.browserPage().setFpsLimit(60));
        test.step("save the browser-resolution image", GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<Void> saved;

            @Override
            public boolean tick(TestContext context) {
                if (this.saved == null) {
                    Path destination = context.client().gameDirectory.toPath()
                            .resolve("screenshots/gametest/glue-test_web-sites/native-lacaleche.png");
                    this.saved = WebGameTest.this.browserPage().screenshot().thenAcceptAsync(image -> {
                        try {
                            if (!ImageIO.write(image, "png", destination.toFile())) throw new IOException("No PNG writer");
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
                }
                if (!this.saved.isDone()) return false;
                this.saved.join();
                return true;
            }
        });
        test.run("navigate to Google", ctx -> this.browser.navigate("https://www.google.com/"))
                .waitUntil("Google paints", ctx -> isPainted(this.browserPage(), false) && this.browserPage().url().contains("google"))
                .waitTicks(40).screenshot("google-chromium");
        Origin page = () -> new int[] {0, BrowserScreen.PAGE_TOP};
        this.inspect(test, "dismiss Google consent if offered", this::browserPage,
                "(()=>{const e=document.getElementById('W0wltc');if(!e)return {present:false};e.scrollIntoView({block:'center'});"
                        + "return {present:true,...e.getBoundingClientRect().toJSON()};})()", box -> {
                    if (box.get("present").getAsBoolean()) {
                        this.pressAndRelease(this.browser::mouseClicked, this.browser::mouseReleased,
                                x(box, 0, 0.5), centerY(box, BrowserScreen.PAGE_TOP));
                    }
                });
        test.waitTicks(40);
        this.clickExpression(test, "focus the Google search field", this::browserPage, page,
                "(()=>{const e=document.querySelector('textarea[name=q],input[name=q]');if(!e)throw new Error('Google search field missing');"
                        + "e.scrollIntoView({block:'center'});return e.getBoundingClientRect().toJSON();})()");
        test.run("type and submit a search", ctx -> {
                    for (char character : "chromium embedded framework".toCharArray()) this.browser.charTyped(character, 0);
                    this.browser.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
                    this.browser.keyReleased(GLFW.GLFW_KEY_ENTER, 0, 0);
                })
                .waitUntil("Google responds to the submitted search", ctx -> {
                    String path = URI.create(this.browserPage().url()).getPath();
                    return isPainted(this.browserPage(), false) && (path.equals("/search") || path.startsWith("/sorry"));
                }, GameTest.LONG_TIMEOUT)
                .run("record the Google response type", ctx -> ctx.log(URI.create(this.browserPage().url()).getPath().startsWith("/sorry")
                        ? "Google returned a site challenge; search results are NOT validated."
                        : "Google returned a search-results URL."))
                .waitTicks(40).screenshot("google-search-native-input")
                .run("navigate to YouTube", ctx -> this.browser.navigate("https://www.youtube.com/"))
                .waitUntil("YouTube loads", ctx -> isPainted(this.browserPage(), false)
                        && this.browserPage().url().contains("youtube"), GameTest.LONG_TIMEOUT)
                .waitTicks(100).screenshot("youtube-chromium")
                .run("close the browser", ctx -> {
                    this.closing = this.browserPage();
                    this.browser.onClose();
                })
                .waitUntil("the browser page is disposed", ctx -> this.closing.stopped().isDone());
        return test;
    }

    /** Server thread; also runs when the server stops early, so a failed run leaves the world as it was. */
    private void restoreGameMode(MinecraftServer server) {
        GameType mode = this.previousMode;
        if (mode == null) return;
        this.previousMode = null;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) player.setGameMode(mode);
    }

    private WebSurface lab() {
        return this.lab.surface().orElseThrow();
    }

    private WebSurface panel() {
        return InventoryPanel.widget().surface().orElse(null);
    }

    private int[] panelOrigin() {
        return new int[] {InventoryPanel.widget().getX(), InventoryPanel.widget().getY()};
    }

    private WebSurface dialog() {
        return Minecraft.getInstance().screen instanceof WebScreen screen ? screen.surface().orElseThrow() : null;
    }

    private WebSurface browserPage() {
        return this.browser.surface().orElse(null);
    }

    private void inspect(GameTest test, String label, Supplier<WebSurface> surface, String expression, Consumer<JsonObject> assertion) {
        test.step(label, GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<JsonElement> result;

            @Override
            public boolean tick(TestContext context) {
                if (this.result == null) this.result = surface.get().evaluate(expression).thenApply(JsonParser::parseString);
                if (!this.result.isDone()) return false;
                assertion.accept(this.result.join().getAsJsonObject());
                return true;
            }
        });
    }

    /** Re-evaluates a boolean expression until the page reports true. */
    private void waitForScript(GameTest test, String label, Supplier<WebSurface> surface, Supplier<String> expression) {
        test.step(label, GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<String> result;

            @Override
            public boolean tick(TestContext context) {
                if (this.result == null) this.result = surface.get().evaluate(expression.get());
                if (!this.result.isDone()) return false;
                boolean done = "true".equals(this.result.join());
                this.result = null;
                return done;
            }
        });
    }

    private void hover(GameTest test, String label, Supplier<WebSurface> surface, Origin origin, String selector) {
        this.pointer(test, label, surface, origin, rect(selector), false);
    }

    private void click(GameTest test, String label, Supplier<WebSurface> surface, Origin origin, String selector) {
        this.pointer(test, label, surface, origin, rect(selector), true);
    }

    private void clickExpression(GameTest test, String label, Supplier<WebSurface> surface, Origin origin, String expression) {
        this.pointer(test, label, surface, origin, expression, true);
    }

    /**
     * Moves the real pointer to the element center, then optionally presses and releases the left
     * button, one input per tick so the screen processes each event.
     */
    private void pointer(GameTest test, String label, Supplier<WebSurface> surface, Origin origin, String expression, boolean click) {
        test.step(label, GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<JsonElement> result;
            private int phase;

            @Override
            public boolean tick(TestContext context) {
                if (this.result == null) this.result = surface.get().evaluate(expression).thenApply(JsonParser::parseString);
                if (!this.result.isDone()) return false;

                JsonObject box = this.result.join().getAsJsonObject();
                int[] offset = origin.get();
                Minecraft client = context.client();
                double scale = client.getWindow().getGuiScale();
                double x = x(box, offset[0], 0.5) * scale;
                double y = centerY(box, offset[1]) * scale;
                switch (this.phase++) {
                    // MouseHandler only resets its origin on the first move after the pointer is released.
                    case 0 -> RealInput.moveToFramebuffer(client, x - 1, y);
                    case 1 -> RealInput.moveToFramebuffer(client, x, y);
                    case 2 -> RealInput.leftButton(client, true);
                    default -> RealInput.leftButton(client, false);
                }
                return this.phase == (click ? 4 : 2);
            }
        });
    }

    private void pressAndRelease(Press press, Press release, double x, double y) {
        press.apply(x, y, 0);
        release.apply(x, y, 0);
    }

    private static boolean isPainted(WebSurface surface, boolean bridged) {
        if (surface == null) return false;
        if (!surface.error().isEmpty()) throw new AssertionError(surface.error());
        return surface.isReady() && !surface.isLoading() && surface.uploadedFrames() > 0 && (!bridged || surface.isConnected());
    }

    private static boolean isSettled(TestContext context) {
        LocalPlayer player = context.player();
        if (player.isDeadOrDying()) {
            player.respawn();
            return false;
        }
        return player.onGround() || player.isInWater() || player.isSpectator() || player.getAbilities().flying;
    }

    private static long countSlots(WebSurface surface, String name) {
        return surface.slots().stream().map(WebSlot::name).filter(name::equals).count();
    }

    private static double x(JsonObject rect, int origin, double ratio) {
        return origin + rect.get("x").getAsDouble() + rect.get("width").getAsDouble() * ratio;
    }

    private static double centerY(JsonObject rect, int origin) {
        return origin + rect.get("y").getAsDouble() + rect.get("height").getAsDouble() / 2;
    }

    private static String rect(String selector) {
        String quoted = "'" + selector.replace("'", "\\'") + "'";
        return "(()=>{const e=document.querySelector(" + quoted + ");if(!e)throw new Error('Missing '+" + quoted + ");"
                + "e.scrollIntoView({block:'center'});return e.getBoundingClientRect().toJSON();})()";
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
    }

    private static boolean failure(String description) {
        throw new AssertionError(description);
    }

    /** The GUI position of a page's top-left corner. */
    @FunctionalInterface
    private interface Origin {

        int[] get();
    }

    @FunctionalInterface
    private interface Press {

        boolean apply(double x, double y, int button);
    }
}
