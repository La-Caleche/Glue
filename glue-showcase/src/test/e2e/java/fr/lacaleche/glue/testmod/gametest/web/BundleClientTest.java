package fr.lacaleche.glue.testmod.gametest.web;

import com.google.gson.JsonObject;
import fr.lacaleche.glue.gametest.ClientTestSpec;
import fr.lacaleche.glue.testmod.gametest.WorldClientTest;
import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebScreen;

/** Native bundle routing, pinned releases, bridge trust and service-worker rejection. */
@SuppressWarnings("PMD.TestClassWithoutTestCases")
@ClientTestSpec("web-bundles")
public final class BundleClientTest extends WorldClientTest {

    @Override
    protected void test() {
        this.waitUntil("Chromium preloaded", client -> WebSurface.runtimeStatus().startsWith("Chromium "));
        WebScreen screen = this.context.computeOnClient(client -> {
            WebApp app = WebApp.builder(WebDemos.MOD_ID, "bundle-test").embedded("1.0.0", "web-bundles")
                    .contract("showcase.bundles/1").register();
            return WebScreen.builder(app.page("index.html")).state("bundle", app::status).open();
        });
        WebTestPage page = new WebTestPage(this.context, () -> screen.surface().orElse(null));
        page.ready(true);
        String address = this.context.computeOnClient(client -> screen.surface().orElseThrow().url());
        require(address.equals("https://bundle-test.glue-showcase.glue/_glue/embedded/index.html"), "Release address is not pinned");
        JsonObject result = page.evaluate("(async()=>({module:(await import('./release.js')).description,root:await(await fetch('/release.js')).text(),connected:document.documentElement.dataset.connected}))()").getAsJsonObject();
        require(result.get("module").getAsString().contains("1.0.0"), "Deferred module must use embedded release");
        require(result.get("root").getAsString().contains("1.0.0"), "Root-relative fetch must use embedded release");
        require(result.get("connected").getAsString().equals("true"), "Managed origin lost bridge trust");
        JsonObject worker = page.evaluate("(async()=>{if(!navigator.serviceWorker)return {blocked:true};try{const r=await navigator.serviceWorker.register('./service-worker.js');await r.unregister();return {blocked:false};}catch(e){return {blocked:true};}})()").getAsJsonObject();
        require(worker.get("blocked").getAsBoolean(), "Managed applications must reject service workers");
        this.screenshot("web-bundles");
        @SuppressWarnings("PMD.CloseResource") // Borrowed from the screen; onClose owns disposal.
        WebSurface surface = this.context.computeOnClient(client -> screen.surface().orElseThrow());
        this.context.runOnClient(client -> surface.reload());
        this.context.waitTicks(5);
        page.ready(true);
        this.game.expect("reload remains pinned", client -> require(surface.url().equals(address), "Release address changed"));
        this.context.runOnClient(client -> screen.onClose());
        new WebTestPage(this.context, () -> surface).disposed();
    }
}
