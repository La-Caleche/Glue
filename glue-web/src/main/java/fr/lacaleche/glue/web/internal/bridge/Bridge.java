package fr.lacaleche.glue.web.internal.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.bridge.WebSlot;
import fr.lacaleche.glue.web.WebSurface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * One surface's page channel. Queries are acknowledged on CEF's thread and handled on the client
 * thread. Results, state and events return by script evaluation, and only into the document that
 * connected; a new document must connect again.
 */
public final class Bridge {

    static final Gson GSON = new GsonBuilder().disableHtmlEscaping().serializeNulls().create();
    static final int MAX_REQUEST_LENGTH = 65_536;
    static final int MAX_PUSH_LENGTH = 1 << 20;
    static final int MAX_SLOTS = 256;
    static final int MAX_IN_FLIGHT = 256;
    public static final Rejection PERSISTENT = new Rejection(ErrorCode.MALFORMED, "Persistent queries are not supported");
    private static final Rejection UNTRUSTED = new Rejection(ErrorCode.UNTRUSTED, "This page is not trusted by its Glue host");
    private static final Rejection BUSY = new Rejection(ErrorCode.BUSY, "Too many bridge messages are pending");
    private static final Rejection CLOSED = new Rejection(ErrorCode.CLOSED, "The web surface is closed");
    private static final int MAX_ERROR_LENGTH = 4096;
    private static final double MAX_COORDINATE = 1_000_000;
    private static final Logger LOGGER = LoggerFactory.getLogger("glue-web");

    private final BridgeSettings settings;
    private final WebSurface surface;
    private final Executor clientThread;
    private final AtomicInteger inFlight = new AtomicInteger();
    private final Map<String, JsonElement> sentStates = new HashMap<>();
    private final Set<String> failingStates = new HashSet<>();
    private volatile PageChannel page;
    private volatile boolean closed;
    private long connectedDocument = -1;
    private long revision;
    private List<SlotRect> slots = List.of();
    private long slotsDocument = -1;
    private Runnable closeHandler;

    /**
     * @param surface      the public surface passed to actions; may be null in tests
     * @param clientThread runs a task on Minecraft's client thread, directly when already on it
     */
    public Bridge(BridgeSettings settings, WebSurface surface, Executor clientThread) {
        this.settings = settings;
        this.surface = surface;
        this.clientThread = clientThread;
    }

    public void attach(PageChannel target) {
        this.page = target;
    }

    public void onCloseRequest(Runnable handler) {
        this.closeHandler = handler;
    }

    /** CEF thread. Returns null once the message is scheduled, otherwise why it was refused. */
    public Rejection accept(boolean mainFrame, String frameUrl, long document, String request) {
        if (this.closed) return CLOSED;
        WebOrigin origin = this.settings.origin();
        if (!mainFrame || origin == null || !origin.matches(frameUrl)) return UNTRUSTED;
        if (request.length() > MAX_REQUEST_LENGTH) {
            return new Rejection(ErrorCode.TOO_LARGE, "Bridge messages are limited to " + MAX_REQUEST_LENGTH + " characters");
        }

        Message message;
        try {
            message = Message.parse(request);
        } catch (IllegalArgumentException exception) {
            return new Rejection(ErrorCode.MALFORMED, exception.getMessage());
        }
        if (this.inFlight.incrementAndGet() > MAX_IN_FLIGHT) {
            this.inFlight.decrementAndGet();
            return BUSY;
        }
        this.clientThread.execute(() -> this.dispatch(document, message));
        return null;
    }

    /** Client thread: publishes the states that changed since the last sample. */
    public void tick() {
        PageChannel target = this.page;
        if (this.closed || target == null || this.settings.states().isEmpty()) return;
        if (this.connectedDocument != target.document()) return;

        JsonObject changed = this.sampleStates(false);
        if (changed.size() == 0) return;

        JsonObject message = new JsonObject();
        message.addProperty("t", "state");
        message.addProperty("revision", ++this.revision);
        message.add("states", changed);
        String script = script(message);
        if (script == null) {
            LOGGER.warn("A web state update exceeds {} characters and was dropped", MAX_PUSH_LENGTH);
            return;
        }
        this.deliver(this.connectedDocument, script);
    }

    /** Client thread. Validates and encodes even when no page is connected; returns whether it was sent. */
    public boolean emit(String event, Object data) {
        if (event == null || !ActionTable.NAME.matcher(event).matches()) {
            throw new IllegalArgumentException("Invalid web event name: " + event);
        }
        JsonObject message = new JsonObject();
        message.addProperty("t", "event");
        message.addProperty("name", event);
        message.add("data", ActionTable.json(data));
        String script = script(message);
        if (script == null) {
            throw new IllegalArgumentException("Web event " + event + " exceeds " + MAX_PUSH_LENGTH + " characters");
        }
        return this.isConnected() && this.deliver(this.connectedDocument, script);
    }

    public boolean isConnected() {
        PageChannel target = this.page;
        return !this.closed && target != null && this.connectedDocument == target.document();
    }

    /** Slots most recently reported by the current document. */
    public List<SlotRect> slots() {
        PageChannel target = this.page;
        return target != null && this.slotsDocument == target.document() ? this.slots : List.of();
    }

    public void close() {
        this.closed = true;
        this.page = null;
        this.closeHandler = null;
        this.sentStates.clear();
        this.slots = List.of();
    }

    private void dispatch(long document, Message message) {
        if (this.closed) {
            this.inFlight.decrementAndGet();
            return;
        }
        switch (message.kind()) {
            case HELLO -> this.connect(document, message.id());
            case CALL -> this.call(document, message);
            case SLOTS -> {
                this.slots = message.slots();
                this.slotsDocument = document;
                this.succeed(document, message.id(), JsonNull.INSTANCE);
            }
            case CLOSE -> {
                Runnable handler = this.closeHandler;
                if (handler == null) {
                    this.fail(document, message.id(), ErrorCode.UNSUPPORTED, "This host cannot be closed by its page");
                } else {
                    this.succeed(document, message.id(), JsonNull.INSTANCE);
                    handler.run();
                }
            }
        }
    }

    private void connect(long document, long id) {
        this.connectedDocument = document;
        this.sentStates.clear();
        JsonObject connection = new JsonObject();
        connection.addProperty("revision", ++this.revision);
        connection.add("states", this.sampleStates(true));
        this.succeed(document, id, connection);
    }

    private void call(long document, Message message) {
        this.settings.actions().invoke(message.name(), message.payload(), this.surface)
                .whenComplete((result, failure) -> this.clientThread.execute(() -> {
                    if (failure == null) this.succeed(document, message.id(), result);
                    else this.fail(document, message.id(), failure);
                }));
    }

    private JsonObject sampleStates(boolean all) {
        JsonObject values = new JsonObject();
        for (Map.Entry<String, Supplier<?>> entry : this.settings.states().entrySet()) {
            String key = entry.getKey();
            JsonElement value;
            try {
                value = ActionTable.json(entry.getValue().get());
            } catch (RuntimeException exception) {
                if (this.failingStates.add(key)) LOGGER.error("Web state {} could not be sampled", key, exception);
                continue;
            }
            this.failingStates.remove(key);
            if (all || !value.equals(this.sentStates.get(key))) {
                this.sentStates.put(key, value);
                values.add(key, value);
            }
        }
        return values;
    }

    private void succeed(long document, long id, JsonElement value) {
        JsonObject reply = reply(id, true);
        reply.add("value", value);
        String script = script(reply);
        if (script == null) {
            this.fail(document, id, ErrorCode.TOO_LARGE, "The result exceeds " + MAX_PUSH_LENGTH + " characters");
            return;
        }
        this.inFlight.decrementAndGet();
        this.deliver(document, script);
    }

    private void fail(long document, long id, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null ? failure.getCause() : failure;
        if (cause instanceof ActionTable.UnknownActionException) {
            this.fail(document, id, ErrorCode.UNKNOWN_ACTION, cause.getMessage());
            return;
        }
        // Argument and state exceptions are how actions report invalid requests; anything else is a defect.
        if (!(cause instanceof IllegalArgumentException) && !(cause instanceof IllegalStateException)) {
            LOGGER.error("Web action failed", cause);
        }
        String message = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        this.fail(document, id, ErrorCode.ACTION_FAILED, message);
    }

    private void fail(long document, long id, ErrorCode code, String message) {
        JsonObject reply = reply(id, false);
        reply.addProperty("code", code.value());
        reply.addProperty("error", message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message);
        this.inFlight.decrementAndGet();
        this.deliver(document, script(reply));
    }

    private boolean deliver(long document, String script) {
        PageChannel target = this.page;
        if (this.closed || target == null || target.document() != document) return false;
        target.run(script);
        return true;
    }

    private static JsonObject reply(long id, boolean ok) {
        JsonObject reply = new JsonObject();
        reply.addProperty("t", "result");
        reply.addProperty("id", id);
        reply.addProperty("ok", ok);
        return reply;
    }

    private static String script(JsonObject message) {
        String json = GSON.toJson(message);
        if (json.length() > MAX_PUSH_LENGTH) return null;
        return "window.__glueBridge&&window.__glueBridge.receive(" + json + ")";
    }

    private static String text(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return element.getAsString();
    }

    private static double number(JsonElement element, String field) {
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        double value = element.getAsDouble();
        if (!Double.isFinite(value)) throw new IllegalArgumentException(field + " must be finite");
        return value;
    }

    /** The document a bridge writes into. Script execution is asynchronous and fire-and-forget. */
    public interface PageChannel {

        /** Increments whenever the main frame starts a new document. */
        long document();

        void run(String script);
    }

    public enum ErrorCode {
        UNTRUSTED(1),
        MALFORMED(2),
        BUSY(3),
        UNKNOWN_ACTION(4),
        ACTION_FAILED(5),
        UNSUPPORTED(6),
        CLOSED(7),
        TOO_LARGE(8);

        private final int value;

        ErrorCode(int value) {
            this.value = value;
        }

        public int value() {
            return this.value;
        }
    }

    public record Rejection(ErrorCode code, String message) {
    }

    enum Kind {
        HELLO,
        CALL,
        SLOTS,
        CLOSE;

        static Kind of(String wire) {
            return switch (wire) {
                case "hello" -> HELLO;
                case "call" -> CALL;
                case "slots" -> SLOTS;
                case "close" -> CLOSE;
                default -> throw new IllegalArgumentException("Unknown bridge message: " + wire);
            };
        }
    }

    record Message(Kind kind, long id, String name, JsonElement payload, List<SlotRect> slots) {

        static Message parse(String request) {
            JsonElement element;
            try {
                element = JsonParser.parseString(request);
            } catch (JsonParseException exception) {
                throw new IllegalArgumentException("Bridge messages must be JSON objects", exception);
            }
            if (!element.isJsonObject()) throw new IllegalArgumentException("Bridge messages must be JSON objects");

            JsonObject object = element.getAsJsonObject();
            double id = number(object.get("id"), "id");
            if (id < 1 || id > 9_007_199_254_740_991.0 || id != Math.rint(id)) {
                throw new IllegalArgumentException("id must be a positive integer");
            }
            Kind kind = Kind.of(text(object.get("t"), "t"));
            return switch (kind) {
                case HELLO, CLOSE -> new Message(kind, (long) id, "", JsonNull.INSTANCE, List.of());
                case CALL -> {
                    String name = text(object.get("name"), "name");
                    if (!ActionTable.NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid web action name");
                    JsonElement payload = object.get("payload");
                    yield new Message(kind, (long) id, name, payload == null ? JsonNull.INSTANCE : payload, List.of());
                }
                case SLOTS -> new Message(kind, (long) id, "", JsonNull.INSTANCE, SlotRect.parseAll(object.get("slots")));
            };
        }
    }

    /** A slot reported by the page, in CSS pixels of the page viewport. */
    public record SlotRect(String name, String argument, double x, double y, double width, double height) {

        static List<SlotRect> parseAll(JsonElement element) {
            if (element == null || !element.isJsonArray()) throw new IllegalArgumentException("slots must be an array");
            JsonArray entries = element.getAsJsonArray();
            if (entries.size() > MAX_SLOTS) throw new IllegalArgumentException("A page reports at most " + MAX_SLOTS + " slots");

            List<SlotRect> slots = new ArrayList<>(entries.size());
            for (JsonElement entry : entries) slots.add(parse(entry));
            return List.copyOf(slots);
        }

        static SlotRect parse(JsonElement element) {
            if (!element.isJsonArray() || element.getAsJsonArray().size() != 5) {
                throw new IllegalArgumentException("Each slot is [id, x, y, width, height]");
            }
            JsonArray values = element.getAsJsonArray();
            String id = text(values.get(0), "slot id");
            if (id.length() > 128) throw new IllegalArgumentException("Slot ids are limited to 128 characters");

            int separator = id.indexOf(':');
            String name = separator < 0 ? id : id.substring(0, separator);
            SlotBinding.validateName(name);
            double[] bounds = new double[4];
            for (int index = 0; index < bounds.length; index++) {
                bounds[index] = number(values.get(index + 1), "slot bounds");
                if (Math.abs(bounds[index]) > MAX_COORDINATE) throw new IllegalArgumentException("Slot bounds are out of range");
            }
            if (bounds[2] < 0 || bounds[3] < 0) throw new IllegalArgumentException("Slot sizes cannot be negative");
            return new SlotRect(name, separator < 0 ? "" : id.substring(separator + 1),
                    bounds[0], bounds[1], bounds[2], bounds[3]);
        }

        /** Maps the page rectangle onto the GUI rectangle the page is drawn into. */
        public WebSlot place(int left, int top, int width, int height, int pageWidth, int pageHeight) {
            double scaleX = (double) width / pageWidth;
            double scaleY = (double) height / pageHeight;
            int x0 = left + (int) Math.round(this.x * scaleX);
            int y0 = top + (int) Math.round(this.y * scaleY);
            int x1 = left + (int) Math.round((this.x + this.width) * scaleX);
            int y1 = top + (int) Math.round((this.y + this.height) * scaleY);
            return new WebSlot(this.name, this.argument, x0, y0, x1 - x0, y1 - y0);
        }
    }
}
