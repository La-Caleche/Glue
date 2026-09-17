package fr.lacaleche.glue.web.internal.bridge;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonParser;
import fr.lacaleche.glue.web.bridge.WebAction;
import fr.lacaleche.glue.web.WebSurface;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionTableTest {

    @Test
    void bindsAnnotatedMethodsByDeclaredOrDefaultName() {
        ActionTable table = ActionTable.compile(List.of(new Waypoints()));
        assertEquals(Set.of("waypoints.create", "count", "ping", "fail", "later", "reject", "identity"), table.names());
    }

    @Test
    void decodesRecordPayloadsAndEncodesResults() throws Exception {
        Waypoints handler = new Waypoints();
        ActionTable table = ActionTable.compile(List.of(handler));

        JsonElement created = table.invoke("waypoints.create", json("{\"name\":\"Base\",\"x\":4}"), null).get();
        assertEquals(json("{\"name\":\"Base\",\"x\":4,\"id\":1}"), created);
        assertEquals(json("1"), table.invoke("count", JsonNull.INSTANCE, null).get());
        assertEquals(JsonNull.INSTANCE, table.invoke("ping", JsonNull.INSTANCE, null).get());
        assertEquals(json("\"done\""), table.invoke("later", JsonNull.INSTANCE, null).get());
    }

    @Test
    void injectsTheCallingSurfaceWithoutConsumingThePayload() throws Exception {
        ActionTable table = ActionTable.compile(List.of(new Waypoints()));
        assertEquals(json("\"null:7\""), table.invoke("identity", json("7"), null).get());
    }

    @Test
    void reportsUnknownActionsDecodingErrorsAndHandlerExceptions() {
        ActionTable table = ActionTable.compile(List.of(new Waypoints()));

        assertInstanceOf(ActionTable.UnknownActionException.class, failure(table.invoke("missing", JsonNull.INSTANCE, null)));
        assertInstanceOf(IllegalArgumentException.class, failure(table.invoke("waypoints.create", json("[1]"), null)));
        assertInstanceOf(IllegalArgumentException.class, failure(table.invoke("identity", JsonNull.INSTANCE, null)));
        Throwable thrown = failure(table.invoke("fail", JsonNull.INSTANCE, null));
        assertInstanceOf(IllegalStateException.class, thrown);
        assertEquals("No base here", thrown.getMessage());
        assertInstanceOf(IllegalArgumentException.class, failure(table.invoke("reject", JsonNull.INSTANCE, null)));
    }

    @Test
    void rejectsInvalidHandlers() {
        assertThrows(IllegalArgumentException.class, () -> ActionTable.compile(List.of(new Object())));
        assertThrows(IllegalArgumentException.class, () -> ActionTable.compile(List.of(new TwoPayloads())));
        assertThrows(IllegalArgumentException.class, () -> ActionTable.compile(List.of(new BadName())));
        assertThrows(IllegalArgumentException.class, () -> ActionTable.compile(List.of(new Waypoints(), new Waypoints())));
        assertTrue(ActionTable.compile(List.of()).isEmpty());
    }

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static Throwable failure(CompletableFuture<JsonElement> future) {
        ExecutionException exception = assertThrows(ExecutionException.class, future::get);
        return exception.getCause();
    }

    record NewWaypoint(String name, int x) {
    }

    record Waypoint(String name, int x, int id) {
    }

    static final class Waypoints {

        private int created;

        @WebAction("waypoints.create")
        private Waypoint create(NewWaypoint request) {
            return new Waypoint(request.name(), request.x(), ++this.created);
        }

        @WebAction
        int count() {
            return 1;
        }

        @WebAction
        static void ping() {
        }

        @WebAction
        void fail() {
            throw new IllegalStateException("No base here");
        }

        @WebAction
        CompletableFuture<String> later() {
            return CompletableFuture.supplyAsync(() -> "done");
        }

        @WebAction
        CompletableFuture<String> reject() {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Rejected"));
        }

        @WebAction
        String identity(WebSurface surface, int value) {
            return surface + ":" + value;
        }
    }

    static final class TwoPayloads {

        @WebAction
        void move(int x, int y) {
        }
    }

    static final class BadName {

        @WebAction("has space")
        void act() {
        }
    }
}
