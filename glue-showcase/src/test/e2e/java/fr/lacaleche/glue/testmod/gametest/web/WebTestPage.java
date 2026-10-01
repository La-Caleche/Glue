package fr.lacaleche.glue.testmod.gametest.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import fr.lacaleche.glue.gametest.ClientTest;
import fr.lacaleche.glue.web.WebSurface;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.function.Supplier;

/** Showcase web fixture; every surface access is marshalled to the client thread. */
public final class WebTestPage {

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    private final ClientGameTestContext context;
    private final ClientTest game;
    private final Supplier<WebSurface> surface;
    private final Supplier<Vector2i> origin;
    private WebSurface observed;

    public WebTestPage(ClientGameTestContext context, Supplier<WebSurface> surface) {
        this(context, surface, Vector2i::new);
    }

    public WebTestPage(ClientGameTestContext context, Supplier<WebSurface> surface, Supplier<Vector2i> origin) {
        this.context = context;
        this.game = new ClientTest(context);
        this.surface = surface;
        this.origin = origin;
    }

    public void ready(boolean bridged) {
        this.game.waitUntil("web page paints" + (bridged ? " and connects" : ""), client -> {
            WebSurface page = this.surface.get();
            if (page == null) return false;
            if (!page.error().isEmpty()) throw new AssertionError(page.error());
            return page.isReady() && !page.isLoading() && page.uploadedFrames() > 0 && (!bridged || page.isConnected());
        }, TIMEOUT);
        this.observed = this.context.computeOnClient(client -> this.surface.get());
    }

    public JsonElement evaluate(String expression) {
        return JsonParser.parseString(this.game.await("JavaScript: " + expression,
                this.context.computeOnClient(client -> this.surface.get().evaluate(expression)), TIMEOUT));
    }

    public void expectScript(String expression) {
        long started = System.nanoTime();
        while (!this.evaluate(expression).getAsBoolean()) {
            if (System.nanoTime() - started >= TIMEOUT.toNanos()) {
                throw new AssertionError("JavaScript condition did not become true: " + expression);
            }
            this.context.waitTick();
        }
    }

    public void click(String selector) {
        this.hover(selector);
        this.clickPointer();
    }

    public void hover(String selector) {
        JsonObject box = this.rectangle(selector);
        this.move(box, 0.5);
        this.context.waitTick();
    }

    public void clickExpression(String expression) {
        this.move(this.evaluate(expression).getAsJsonObject(), 0.5);
        this.clickPointer();
    }

    public void drag(String selector, double from, double to) {
        JsonObject box = this.rectangle(selector);
        this.move(box, from);
        this.context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        try {
            this.context.waitTick();
            this.move(box, to);
            this.context.waitTick();
        } finally {
            this.context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        this.context.waitTick();
    }

    public void disposed() {
        this.game.await("web page disposal", this.context.computeOnClient(client ->
                (this.observed == null ? this.surface.get() : this.observed).stopped()), TIMEOUT);
    }

    private JsonObject rectangle(String selector) {
        String quoted = new JsonPrimitive(selector).toString();
        String expression = "(()=>{const e=document.querySelector(" + quoted + ");if(!e)return null;"
                + "e.scrollIntoView({block:'center'});const r=e.getBoundingClientRect();"
                + "return r.width>0&&r.height>0?r.toJSON():null;})()";
        long started = System.nanoTime();
        while (true) {
            JsonElement result = this.evaluate(expression);
            if (!result.isJsonNull()) return result.getAsJsonObject();
            if (System.nanoTime() - started >= TIMEOUT.toNanos()) throw new AssertionError("Missing visible element: " + selector);
            this.context.waitTick();
        }
    }

    private void move(JsonObject box, double horizontalFraction) {
        Vector2i offset = this.context.computeOnClient(client -> this.origin.get());
        this.game.movePointer(offset.x + box.get("x").getAsDouble() + box.get("width").getAsDouble() * horizontalFraction,
                offset.y + box.get("y").getAsDouble() + box.get("height").getAsDouble() / 2);
    }

    private void clickPointer() {
        this.context.getInput().holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        try {
            this.context.waitTick();
        } finally {
            this.context.getInput().releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        this.context.waitTick();
    }
}
