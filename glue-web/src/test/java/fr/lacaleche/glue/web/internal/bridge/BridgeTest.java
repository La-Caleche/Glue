package fr.lacaleche.glue.web.internal.bridge;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.bridge.WebSlotRenderer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeTest {

    private static final String PAGE = "https://demo.glue/index.html";
    private static final WebSlotRenderer NO_DRAWING = (graphics, slot) -> { };

    private final Page page = new Page();
    private final AtomicInteger health = new AtomicInteger(20);

    @Test
    void connectsTrustedMainFrameDocumentsWithAStateSnapshot() {
        Bridge bridge = this.bridge(Map.of());
        assertFalse(bridge.isConnected());

        assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1}"));
        JsonObject reply = this.page.next();
        assertEquals("result", reply.get("t").getAsString());
        assertEquals(1, reply.get("id").getAsLong());
        assertTrue(reply.get("ok").getAsBoolean());
        assertEquals(20, reply.getAsJsonObject("value").getAsJsonObject("states").get("health").getAsInt());
        assertTrue(bridge.isConnected());
    }

    @Test
    void refusesUntrustedFramesAndMalformedMessages() {
        Bridge bridge = this.bridge(Map.of());
        assertEquals(Bridge.ErrorCode.UNTRUSTED, bridge.accept(false, PAGE, 0, "{\"t\":\"hello\",\"id\":1}").code());
        assertEquals(Bridge.ErrorCode.UNTRUSTED, bridge.accept(true, "https://other.glue/", 0, "{\"t\":\"hello\",\"id\":1}").code());
        assertEquals(Bridge.ErrorCode.UNTRUSTED, bridge.accept(true, "http://demo.glue/", 0, "{\"t\":\"hello\",\"id\":1}").code());
        for (String request : new String[] {"", "[]", "{\"t\":\"hello\"}", "{\"t\":\"hello\",\"id\":0}",
                "{\"t\":\"hello\",\"id\":1.5}", "{\"t\":\"shout\",\"id\":1}", "{\"t\":\"call\",\"id\":1}",
                "{\"t\":\"call\",\"id\":1,\"name\":\"bad name\"}", "{\"t\":\"slots\",\"id\":1,\"slots\":[[\"a\",1,2,3]]}",
                "{\"t\":\"slots\",\"id\":1,\"slots\":[[\"a\",1,2,-3,4]]}", "{\"t\":\"slots\",\"id\":1,\"slots\":[[\"9a\",1,2,3,4]]}"}) {
            Bridge.Rejection rejection = bridge.accept(true, PAGE, 0, request);
            assertNotNull(rejection, request);
            assertEquals(Bridge.ErrorCode.MALFORMED, rejection.code(), request);
        }
        String oversized = "{\"t\":\"call\",\"id\":1,\"name\":\"a\",\"payload\":\"" + "x".repeat(Bridge.MAX_REQUEST_LENGTH) + "\"}";
        assertEquals(Bridge.ErrorCode.TOO_LARGE, bridge.accept(true, PAGE, 0, oversized).code());
        assertTrue(this.page.scripts.isEmpty());

        Bridge closed = new Bridge(BridgeSettings.NONE, null, Runnable::run);
        assertEquals(Bridge.ErrorCode.UNTRUSTED, closed.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1}").code());
        closed.close();
        assertEquals(Bridge.ErrorCode.CLOSED, closed.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1}").code());
    }

    @Test
    void answersActionsWithResultsOrCodedErrors() {
        Bridge bridge = this.bridge(Map.of());
        assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"call\",\"id\":2,\"name\":\"heal\",\"payload\":{\"amount\":5}}"));
        JsonObject healed = this.page.next();
        assertTrue(healed.get("ok").getAsBoolean());
        assertEquals(25, healed.get("value").getAsInt());

        assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"call\",\"id\":3,\"name\":\"heal\",\"payload\":{\"amount\":-1}}"));
        JsonObject refused = this.page.next();
        assertFalse(refused.get("ok").getAsBoolean());
        assertEquals(Bridge.ErrorCode.ACTION_FAILED.value(), refused.get("code").getAsInt());
        assertEquals("Healing must be positive", refused.get("error").getAsString());

        assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"call\",\"id\":4,\"name\":\"missing\"}"));
        assertEquals(Bridge.ErrorCode.UNKNOWN_ACTION.value(), this.page.next().get("code").getAsInt());
    }

    @Test
    void publishesOnlyChangedStateToTheConnectedDocument() {
        Bridge bridge = this.bridge(Map.of());
        bridge.tick();
        assertTrue(this.page.scripts.isEmpty(), "No state before a document connects");

        bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1}");
        long connected = this.page.next().getAsJsonObject("value").get("revision").getAsLong();
        bridge.tick();
        assertTrue(this.page.scripts.isEmpty(), "Unchanged state is not resent");

        this.health.set(12);
        bridge.tick();
        JsonObject update = this.page.next();
        assertEquals("state", update.get("t").getAsString());
        assertTrue(update.get("revision").getAsLong() > connected);
        assertEquals(12, update.getAsJsonObject("states").get("health").getAsInt());

        this.page.document = 1;
        this.health.set(4);
        bridge.tick();
        assertTrue(this.page.scripts.isEmpty(), "A new document must connect again");
        assertFalse(bridge.isConnected());
        assertFalse(bridge.emit("damage", 3));
    }

    @Test
    void emitsEventsOnlyWhileConnected() {
        Bridge bridge = this.bridge(Map.of());
        assertFalse(bridge.emit("damage", Map.of("amount", 3)));
        assertThrows(IllegalArgumentException.class, () -> bridge.emit("not valid", null));

        bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1}");
        this.page.next();
        assertTrue(bridge.emit("damage", Map.of("amount", 3)));
        JsonObject event = this.page.next();
        assertEquals("event", event.get("t").getAsString());
        assertEquals("damage", event.get("name").getAsString());
        assertEquals(3, event.getAsJsonObject("data").get("amount").getAsInt());
        assertThrows(IllegalArgumentException.class, () -> bridge.emit("huge", "x".repeat(Bridge.MAX_PUSH_LENGTH)));
    }

    @Test
    void keepsSlotsOfTheCurrentDocumentAndMapsThemToTheGui() {
        Bridge bridge = this.bridge(Map.of("item", new SlotBinding(SlotBinding.Layer.ABOVE, NO_DRAWING)));
        assertNull(bridge.accept(true, PAGE, 0,
                "{\"t\":\"slots\",\"id\":5,\"slots\":[[\"item:3\",10,20,16,16],[\"map\",0.5,0,40.25,40]]}"));
        assertTrue(this.page.next().get("ok").getAsBoolean());

        List<Bridge.SlotRect> slots = bridge.slots();
        assertEquals(2, slots.size());
        assertEquals("item", slots.getFirst().name());
        assertEquals("3", slots.getFirst().argument());
        assertEquals("", slots.get(1).argument());
        assertEquals(new WebSlot("item", "3", 120, 240, 32, 32), slots.getFirst().place(100, 200, 400, 300, 200, 150));

        this.page.document = 1;
        assertTrue(bridge.slots().isEmpty());
    }

    @Test
    void closeRequestsNeedAHostHandler() {
        Bridge bridge = this.bridge(Map.of());
        bridge.accept(true, PAGE, 0, "{\"t\":\"close\",\"id\":6}");
        assertEquals(Bridge.ErrorCode.UNSUPPORTED.value(), this.page.next().get("code").getAsInt());

        AtomicInteger closes = new AtomicInteger();
        bridge.onCloseRequest(closes::incrementAndGet);
        bridge.accept(true, PAGE, 0, "{\"t\":\"close\",\"id\":7}");
        assertTrue(this.page.next().get("ok").getAsBoolean());
        assertEquals(1, closes.get());
    }

    @Test
    void boundsMessagesWaitingForTheClientThread() {
        Queue<Runnable> clientThread = new ArrayDeque<>();
        Bridge bridge = new Bridge(this.settings(Map.of()), null, clientThread::add);
        bridge.attach(this.page);
        for (int id = 1; id <= Bridge.MAX_IN_FLIGHT; id++) {
            assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":" + id + "}"));
        }
        assertEquals(Bridge.ErrorCode.BUSY, bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":999}").code());

        clientThread.poll().run();
        assertNull(bridge.accept(true, PAGE, 0, "{\"t\":\"hello\",\"id\":1000}"));
    }

    private Bridge bridge(Map<String, SlotBinding> slots) {
        Bridge bridge = new Bridge(this.settings(slots), null, Runnable::run);
        bridge.attach(this.page);
        return bridge;
    }

    private BridgeSettings settings(Map<String, SlotBinding> slots) {
        Map<String, Supplier<?>> states = new LinkedHashMap<>();
        states.put("health", this.health::get);
        return new BridgeSettings(WebOrigin.from(URI.create(PAGE)), ActionTable.compile(List.of(new Healing(this.health))),
                states, slots);
    }

    record Heal(int amount) {
    }

    static final class Healing {

        private final AtomicInteger health;

        Healing(AtomicInteger health) {
            this.health = health;
        }

        @WebAction
        int heal(Heal request) {
            if (request.amount() <= 0) throw new IllegalArgumentException("Healing must be positive");
            return this.health.addAndGet(request.amount());
        }
    }

    private static final class Page implements Bridge.PageChannel {

        private static final String PREFIX = "window.__glueBridge&&window.__glueBridge.receive(";

        final List<String> scripts = new ArrayList<>();
        long document;

        @Override
        public long document() {
            return this.document;
        }

        @Override
        public void run(String script) {
            this.scripts.add(script);
        }

        JsonObject next() {
            assertFalse(this.scripts.isEmpty(), "Expected a script for the page");
            String script = this.scripts.removeFirst();
            assertTrue(script.startsWith(PREFIX) && script.endsWith(")"), script);
            return JsonParser.parseString(script.substring(PREFIX.length(), script.length() - 1)).getAsJsonObject();
        }
    }
}
