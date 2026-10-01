package fr.lacaleche.glue.testmod.gametest.web;

import com.google.gson.JsonObject;
import fr.lacaleche.glue.testmod.Testmod;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.testmod.web.browser.BrowserScreen;
import fr.lacaleche.glue.testmod.web.inventory.InventoryPanel;
import fr.lacaleche.glue.testmod.web.lab.LabActions;
import fr.lacaleche.glue.testmod.web.waypoint.Waypoints;
import fr.lacaleche.glue.web.WebCursor;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.host.WebScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.Map;

/** Input, JavaScript bridge, stacked screens, HUD slots and browser isolation in a real client. */
@SuppressWarnings({"PMD.TestClassWithoutTestCases", "PMD.CompareObjectsWithEquals"})
public final class WebClientTest extends WorldClientTest {

    @Override
    protected void test() {
        boolean toastsEnabled = this.context.computeOnClient(client -> WebDemos.toasts().overlay().isEnabled());
        this.waitUntil("Chromium preloaded", client -> WebSurface.runtimeStatus().startsWith("Chromium "));
        @SuppressWarnings("PMD.CloseResource") // Already closed on the client thread; retain only to await disposal.
        WebSurface closing = this.context.computeOnClient(client -> {
            WebSurface surface = WebSurface.builder(URI.create("about:blank")).size(32, 32).transparent(false).open();
            surface.close();
            return surface;
        });
        new WebTestPage(this.context, () -> closing).disposed();

        try {
            this.context.getInput().pressKey(GLFW.GLFW_KEY_F6);
            WebScreen hub = this.screen();
            WebTestPage page = new WebTestPage(this.context, () -> hub.surface().orElse(null));
            page.ready(true);
            this.game.expect("hub app origin", client -> require(hub.surface().orElseThrow().url().equals("https://glue-showcase.glue/index.html"), "Wrong hub origin"));
            JsonObject viewport = page.evaluate("({ratio:devicePixelRatio,width:innerWidth,height:innerHeight,renderer:document.documentElement.dataset.renderer})").getAsJsonObject();
            this.context.runOnClient(client -> {
                require("react".equals(viewport.get("renderer").getAsString()), "Hub must mount React");
                require(viewport.get("ratio").getAsDouble() == client.getWindow().getGuiScale(), "CSS device pixel ratio");
                require(viewport.get("width").getAsInt() == hub.width && viewport.get("height").getAsInt() == hub.height, "CSS viewport dimensions");
            });
            this.screenshot("web-hub");
            this.lab(hub, page);
            this.layers(page);
            this.inventory();
            this.untrustedBrowser();

            // Deliberately unhosted: module shutdown must dispose it after this test returns.
            @SuppressWarnings("PMD.CloseResource") // This scenario explicitly verifies module-owned shutdown disposal.
            WebSurface shutdown = this.context.computeOnClient(client -> {
                WebSurface surface = WebSurface.builder(URI.create("about:blank")).size(32, 32).open();
                surface.stopped().thenRun(() -> Testmod.LOGGER.info("Module-owned web surface disposed"));
                return surface;
            });
            this.waitUntil("module-owned surface ready", client -> shutdown.isReady());
        } finally {
            this.context.runOnClient(client -> {
                WebDemos.vitals().setEnabled(false);
                WebDemos.minimap().setEnabled(false);
                // Dispose the toast document as well: its JS timers otherwise outlive this world.
                WebDemos.toasts().overlay().setEnabled(false);
                WebDemos.toasts().overlay().setEnabled(toastsEnabled);
            });
        }
    }

    private void lab(WebScreen hub, WebTestPage hubPage) {
        hubPage.click("[data-demo=lab]");
        this.waitUntil("lab opens above hub", client -> client.screen instanceof WebScreen screen && screen != hub && screen.parent() == hub);
        WebScreen lab = this.screen();
        WebTestPage page = new WebTestPage(this.context, () -> lab.surface().orElse(null));
        page.ready(true);
        @SuppressWarnings("PMD.CloseResource") // Borrowed from the lab screen, closed through Escape.
        WebSurface surface = this.context.computeOnClient(client -> lab.surface().orElseThrow());
        this.game.expect("hub retained", client -> require(!hub.surface().orElseThrow().isClosed(), "Opening lab closed hub"));

        page.hover("#increment");
        this.waitUntil("hand cursor", client -> surface.cursor() == WebCursor.HAND && surface.appliedCursor() == WebCursor.HAND);
        page.hover("#callsign");
        this.waitUntil("text cursor", client -> surface.appliedCursor() == WebCursor.TEXT);
        page.evaluate("(()=>{document.getElementById('callsign').style.cursor='crosshair';return null;})()");
        this.waitUntil("asynchronous cursor change", client -> surface.appliedCursor() == WebCursor.CROSSHAIR);
        page.evaluate("(()=>{document.getElementById('callsign').style.removeProperty('cursor');return null;})()");
        this.waitUntil("cached text cursor", client -> surface.appliedCursor() == WebCursor.TEXT);
        page.expectScript("labSnapshot().renderer === 'vanilla' && labSnapshot().connected && labSnapshot().fps > 0");
        int increments = this.context.computeOnClient(client -> LabActions.increments());
        page.click("#increment");
        this.waitUntil("page-to-Java action", client -> LabActions.increments() == increments + 1);
        page.expectScript("labSnapshot().count === " + (increments + 1));

        page.click("#callsign");
        this.context.runOnClient(client -> {
            // Fabric 1.21.8's synthetic keyboard callback passes no modifier bits to the host.
            lab.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
            lab.keyReleased(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        });
        this.context.getInput().typeChars("Étoile");
        page.expectScript("labSnapshot().name === 'Étoile'");
        page.drag("#power", 0.25, 0.8);
        page.expectScript("Math.abs(labSnapshot().power - 80) <= 3");
        this.screenshot("web-lab-input");
        page.click("#effect");
        this.waitUntil("select popup composited", client -> surface.hasPopup());
        this.screenshot("web-lab-select");
        this.context.getInput().pressKey(GLFW.GLFW_KEY_DOWN);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        page.expectScript("labSnapshot().effect === 'Ocean'");

        page.click("#greet");
        page.expectScript("labSnapshot().greeting === 'Hello from Chromium, Étoile'");
        page.click("#refuse");
        page.expectScript("labSnapshot().refusal === 'The lab refuses this request on purpose'");
        this.context.runOnClient(client -> require(lab.emit("lab.ping", Map.of("message", "pong")), "Java event was not sent"));
        page.expectScript("labSnapshot().lastEvent?.message === 'pong'");
        page.click("#animate");
        long frames = this.context.computeOnClient(client -> surface.uploadedFrames());
        this.waitUntil("animated Chromium frames", client -> surface.uploadedFrames() - frames > 10);
        this.context.runOnClient(client -> Testmod.LOGGER.info("Web delivery: {}", surface.metrics()));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.waitUntil("same hub restored", client -> client.screen == hub);
        page.disposed();
        hubPage.ready(true);
    }

    private void layers(WebTestPage page) {
        page.click("[data-layer=hud]");
        page.click("[data-layer=minimap]");
        this.waitUntil("HUD layers enabled", client -> WebDemos.vitals().isEnabled() && WebDemos.minimap().isEnabled());
        this.world.getServer().runOnServer(server -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            player.getInventory().add(new ItemStack(Items.DIAMOND_SWORD));
            player.getInventory().add(new ItemStack(Items.GRASS_BLOCK));
            player.getInventory().add(new ItemStack(Items.TORCH));
            player.inventoryMenu.broadcastChanges();
        });
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.context.waitForScreen(null);
        page.disposed();
        this.context.runOnClient(client -> client.player.getInventory().setSelectedSlot(2));
        this.waitUntil("HUD layers visible", client -> WebDemos.vitals().isShowing() && WebDemos.minimap().isShowing());
        this.waitUntil("item and terrain slots", client -> countSlots(WebDemos.vitals().surface().orElseThrow(), "item") >= 9
                && countSlots(WebDemos.minimap().surface().orElseThrow(), "terrain") == 1);
        WebTestPage hud = new WebTestPage(this.context, () -> WebDemos.vitals().surface().orElseThrow());
        hud.expectScript("document.querySelector('.tile.is-selected')?.dataset.glueSlot === 'item:2'");
        this.screenshot("web-hud");
        this.game.expect("HUD input", client -> require(client.screen == null && client.mouseHandler.isMouseGrabbed(), "HUD captured gameplay input"));
        this.context.runOnClient(client -> WebDemos.toast("Scripted toast", "Shown above the inventory."));
        this.waitUntil("toast connected", client -> WebDemos.toasts().overlay().isShowing());
    }

    private void inventory() {
        this.context.runOnClient(client -> client.player.getRecipeBook().setOpen(RecipeBookType.CRAFTING, false));
        this.game.openInventory();
        InventoryScreen inventory = this.context.computeOnClient(client -> (InventoryScreen) client.screen);
        this.game.expect("panel fits inventory", client -> require(InventoryPanel.widget().visible, "Inventory panel is outside the screen"));
        WebTestPage panel = new WebTestPage(this.context, () -> InventoryPanel.widget().surface().orElse(null),
                () -> new Vector2i(InventoryPanel.widget().getX(), InventoryPanel.widget().getY()));
        panel.ready(true);
        this.screenshot("web-inventory-panel-and-toast");
        int camps = this.context.computeOnClient(client -> Waypoints.all().size());
        panel.click("#mark");
        this.waitUntil("camp created", client -> Waypoints.all().size() == camps + 1);
        panel.click("#open");
        this.waitUntil("waypoints above inventory", client -> client.screen instanceof WebScreen screen && screen.parent() == inventory);
        WebScreen waypoints = this.screen();
        WebTestPage page = new WebTestPage(this.context, () -> waypoints.surface().orElse(null));
        page.ready(true);
        page.click("#name");
        this.context.getInput().typeChars("Base");
        page.click("#add");
        this.waitUntil("Java stored waypoint", client -> Waypoints.all().stream().anyMatch(waypoint -> waypoint.name().equals("Base")));
        page.expectScript("[...document.querySelectorAll('li .name')].some(name => name.textContent === 'Base')");
        this.screenshot("web-waypoints");
        page.clickExpression("(()=>{const row=[...document.querySelectorAll('li')].find(li=>li.querySelector('.name').textContent==='Base');"
                + "if(!row)throw new Error('Base is not listed');return row.querySelector('.remove').getBoundingClientRect().toJSON();})()");
        this.waitUntil("stacked confirmation", client -> client.screen instanceof WebScreen screen && screen.parent() == waypoints);
        WebScreen dialog = this.screen();
        WebTestPage confirmation = new WebTestPage(this.context, () -> dialog.surface().orElse(null));
        confirmation.ready(true);
        this.game.expect("underlying pages retained", client -> require(!waypoints.surface().orElseThrow().isClosed()
                && !InventoryPanel.widget().surface().orElseThrow().isClosed(), "Stacking closed an underlying page"));
        this.screenshot("web-confirm-stacked");
        confirmation.click("#remove");
        this.waitUntil("confirmation returns to waypoints", client -> client.screen == waypoints);
        confirmation.disposed();
        this.game.expect("waypoint removed", client -> require(Waypoints.all().stream().noneMatch(waypoint -> waypoint.name().equals("Base")), "Waypoint retained"));
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        this.waitUntil("back to inventory", client -> client.screen == inventory);
        page.disposed();
        panel.ready(true);
        this.context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        panel.disposed();
        @SuppressWarnings("PMD.CloseResource") // The HUD owns it and is disabled immediately below.
        WebSurface hud = this.context.computeOnClient(client -> WebDemos.vitals().surface().orElseThrow());
        this.context.runOnClient(client -> {
            WebDemos.vitals().setEnabled(false);
            WebDemos.minimap().setEnabled(false);
        });
        this.game.expect("vanilla HUD restored", client -> require(!WebDemos.vitals().isShowing()
                && WebDemos.vitals().surface().isEmpty() && WebDemos.minimap().surface().isEmpty(), "Web HUD remained attached"));
        new WebTestPage(this.context, () -> hud).disposed();
        this.screenshot("web-vanilla-hud-restored");
    }

    private void untrustedBrowser() {
        BrowserScreen browser = this.context.computeOnClient(client -> BrowserScreen.open(WebDemos.app().page("lab.html").toString()));
        WebTestPage page = new WebTestPage(this.context, () -> browser.surface().orElse(null), () -> new Vector2i(0, BrowserScreen.PAGE_TOP));
        page.ready(false);
        JsonObject result = page.evaluate("(async()=>{await new Promise(r=>setTimeout(r,200));return {bridge:document.documentElement.dataset.bridge};})()").getAsJsonObject();
        require("offline".equals(result.get("bridge").getAsString()), "Untrusted browser granted the bridge");
        this.game.expect("browser disconnected", client -> require(!browser.surface().orElseThrow().isConnected(), "Unexpected page trust"));
        page.hover("#animate");
        this.waitUntil("widget hand cursor", client -> browser.surface().orElseThrow().appliedCursor() == WebCursor.HAND);
        this.game.movePointer(4, 4);
        this.waitUntil("toolbar arrow cursor", client -> browser.surface().orElseThrow().appliedCursor() == WebCursor.ARROW);
        this.screenshot("web-browser-untrusted");
        this.context.runOnClient(client -> browser.onClose());
        page.disposed();
    }

    private WebScreen screen() {
        this.context.waitForScreen(WebScreen.class);
        return this.context.computeOnClient(client -> (WebScreen) client.screen);
    }

    private static long countSlots(WebSurface surface, String name) {
        return surface.slots().stream().map(WebSlot::name).filter(name::equals).count();
    }
}
