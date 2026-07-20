package fr.lacaleche.glue.testmod.mcsx;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.controller.UIController;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;

import java.util.List;

/**
 * The reference demo controller, bound to {@code assets/mcsx/ui/demo.mcsx}. A click on the button
 * calls {@link #ping()} → the {@code count} signal updates → the {@code label} computed recomputes →
 * the bound {@code <text>} re-renders. Fields/methods are private; the binder reflects into them.
 */
@UIController("mcsx:demo")
public final class DemoController extends ScreenController {

    private final Signal<Integer> count = signal(0);
    private final Signal<Boolean> enabled = signal(true);
    private final Signal<Integer> volume = signal(50);
    private final Signal<String> choice = signal("a");
    private final List<String> choices = List.of("a", "b");
    private final Signal<String> name = signal("");
    private final Computed<Float> volumeFraction = computed(() -> volume.get() / 100f);
    private final Computed<String> label = computed(() ->
            "Count: " + count.get() + "   |   volume " + volume.get()
                    + (enabled.get() ? "   |   on" : "   |   off") + "   |   " + choice.get());

    private void ping() {
        count.update(n -> n + 1);
    }
}
