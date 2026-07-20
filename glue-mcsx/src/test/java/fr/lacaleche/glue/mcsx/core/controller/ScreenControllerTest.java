package fr.lacaleche.glue.mcsx.core.controller;

import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Effect;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScreenControllerTest {

    @UIController("mcsx:demo")
    static final class Demo extends ScreenController {
        final Signal<Integer> count = signal(0);
        final Computed<String> label = computed(() -> "Count: " + count.get());

        void ping() {
            count.update(n -> n + 1);
        }
    }

    @Test
    void factoriesProduceReactiveStateWiredToHandlers() {
        Demo demo = new Demo();
        List<String> seen = new ArrayList<>();
        Effect.of(() -> seen.add(demo.label.get()));

        assertEquals(List.of("Count: 0"), seen);
        demo.ping();
        demo.ping();
        assertEquals(List.of("Count: 0", "Count: 1", "Count: 2"), seen);
    }

    @Test
    void controllerAnnotationCarriesDocumentId() {
        assertEquals("mcsx:demo", Demo.class.getAnnotation(UIController.class).value());
    }
}
