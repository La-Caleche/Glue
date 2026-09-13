package fr.lacaleche.glue.mcsx.client.reactive;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

final class ClientMirrorTest {

    @Test
    void setUpdatesTheOwningCopyAndPublishesToTheUiValue() {
        ClientMirror<String> mirror = ClientMirror.of("draft");
        List<String> published = new ArrayList<>();
        mirror.value().subscribe(published::add);

        assertEquals("draft", mirror.get());
        assertEquals("draft", mirror.value().get());

        mirror.set("active");

        assertEquals("active", mirror.get());
        assertEquals("active", mirror.value().get());
        assertEquals(List.of("active"), published);
    }

    @Test
    void equalValuesNeverCrossToTheUiValue() {
        ClientMirror<List<String>> mirror = ClientMirror.of(List.of("stable"));
        List<List<String>> published = new ArrayList<>();
        mirror.value().subscribe(published::add);

        mirror.set(List.of("stable"));
        mirror.set(List.of("changed"));
        mirror.set(List.of("changed"));

        assertEquals(List.of(List.of("changed")), published);
    }

    @Test
    void readsAndWritesAreRejectedOffTheOwningThread() throws InterruptedException {
        Thread owner = Thread.currentThread();
        ClientMirror<String> mirror = ClientMirror.of("draft", () -> Thread.currentThread() == owner);
        mirror.set("active");

        List<Class<?>> rejections = new ArrayList<>();
        Thread foreign = new Thread(() -> {
            rejections.add(rejection(mirror::get));
            rejections.add(rejection(() -> mirror.set("stolen")));
        }, "client-mirror-foreign");
        foreign.start();
        foreign.join();

        assertEquals(
                List.of(IllegalStateException.class, IllegalStateException.class),
                rejections
        );
        assertEquals("active", mirror.get());
        assertEquals("active", mirror.value().get());
    }

    @Test
    void supportsNullValues() {
        ClientMirror<String> mirror = ClientMirror.of(null);

        assertNull(mirror.get());
        assertNull(mirror.value().get());

        mirror.set("value");
        mirror.set(null);

        assertNull(mirror.get());
        assertNull(mirror.value().get());
    }

    private static Class<?> rejection(Runnable operation) {
        try {
            operation.run();
            return null;
        } catch (RuntimeException failure) {
            return failure.getClass();
        }
    }
}
