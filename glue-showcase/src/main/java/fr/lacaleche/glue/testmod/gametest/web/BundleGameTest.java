package fr.lacaleche.glue.testmod.gametest.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.gametest.GameTest;
import fr.lacaleche.glue.gametest.GameTests;
import fr.lacaleche.glue.gametest.TestContext;
import fr.lacaleche.glue.testmod.web.WebDemos;
import fr.lacaleche.glue.web.WebSurface;
import fr.lacaleche.glue.web.app.WebApp;
import fr.lacaleche.glue.web.host.WebScreen;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Native routing and bridge checks require no remote host, signing key, or publisher credentials. */
final class BundleGameTest {

    private WebScreen screen;
    private WebSurface surface;
    private String address;

    static void register() {
        GameTests.register("glue-test:web-bundles", () -> new BundleGameTest().build());
    }

    private GameTest build() {
        GameTest test = GameTest.create("glue-test:web-bundles").waitForWorld()
                .waitUntil("Chromium is ready", ctx -> WebSurface.runtimeStatus().startsWith("Chromium "), GameTest.LONG_TIMEOUT * 5)
                .run("register a versioned app after CEF startup", ctx -> {
                    WebApp app = WebApp.builder(WebDemos.MOD_ID, "bundle-test").embedded("1.0.0", "web-bundles")
                            .contract("showcase.bundles/1").register();
                    this.screen = WebScreen.builder(app.page("index.html")).state("bundle", app::status).open();
                })
                .waitUntil("versioned page paints and connects its bridge", ctx -> this.painted(), GameTest.LONG_TIMEOUT)
                .run("record the immutable page address", ctx -> this.address = this.surface.url())
                .expect("embedded release has a versioned path", ctx -> this.address.equals("https://bundle-test.glue-showcase.glue/_glue/embedded/index.html"));
        this.inspect(test, "relative imports and root-relative requests resolve to the pinned bundle",
                "(async()=>({module:(await import('./release.js')).description,root:await(await fetch('/release.js')).text(),connected:document.documentElement.dataset.connected}))()",
                result -> {
                    require(result.get("module").getAsString().contains("1.0.0"), "Deferred module must come from the embedded release");
                    require(result.get("root").getAsString().contains("1.0.0"), "Root-relative fetch must use the same release");
                    require(result.get("connected").getAsString().equals("true"), "Managed origin must retain bridge trust");
                });
        this.inspect(test, "service workers cannot replace the bundle resource handler",
                "(async()=>{if(!navigator.serviceWorker)return {blocked:true};try{const r=await navigator.serviceWorker.register('./service-worker.js');await r.unregister();return {blocked:false};}catch(e){return {blocked:true};}})()",
                result -> require(result.get("blocked").getAsBoolean(), "Managed applications must reject service worker scripts"));
        return test.screenshot("web-bundles")
                .run("reload the pinned page", ctx -> this.surface.reload())
                .waitTicks(5)
                .waitUntil("reloaded page reconnects", ctx -> this.painted())
                .expect("reload keeps its release address", ctx -> this.surface.url().equals(this.address))
                .run("close the bundle screen", ctx -> this.screen.onClose())
                .waitUntil("the bundle surface is disposed", ctx -> this.surface.stopped().isDone());
    }

    private boolean painted() {
        this.surface = this.screen.surface().orElse(null);
        if (this.surface == null) return false;
        if (!this.surface.error().isEmpty()) throw new AssertionError(this.surface.error());
        return this.surface.isReady() && !this.surface.isLoading() && this.surface.isConnected() && this.surface.uploadedFrames() > 0;
    }

    private void inspect(GameTest test, String label, String expression, Consumer<JsonObject> assertion) {
        test.step(label, GameTest.DEFAULT_TIMEOUT, new GameTest.StepTick() {
            private CompletableFuture<String> result;

            @Override
            public boolean tick(TestContext context) {
                if (this.result == null) this.result = BundleGameTest.this.surface.evaluate(expression);
                if (!this.result.isDone()) return false;
                assertion.accept(JsonParser.parseString(this.result.join()).getAsJsonObject());
                return true;
            }
        });
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
