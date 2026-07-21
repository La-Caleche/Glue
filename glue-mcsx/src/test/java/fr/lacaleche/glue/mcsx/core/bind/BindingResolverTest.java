package fr.lacaleche.glue.mcsx.core.bind;

import fr.lacaleche.glue.mcsx.core.controller.ScreenController;
import fr.lacaleche.glue.mcsx.core.mcsx.McsxElement;
import fr.lacaleche.glue.mcsx.core.reactive.Computed;
import fr.lacaleche.glue.mcsx.core.reactive.Signal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindingResolverTest {

    record Item(String name, Signal<Boolean> open) {
    }

    static final class Demo extends ScreenController {
        final Signal<Integer> count = signal(7);
        final Computed<String> label = computed(() -> "n=" + count.get());
        final Item item = new Item("first", new Signal<>(true));
        String lastArgument;

        void reset() {
            lastArgument = "reset";
        }

        void remove(Object what) {
            lastArgument = String.valueOf(what);
        }
    }

    private final Demo controller = new Demo();
    private final BindingResolver resolver = new BindingResolver(controller);
    private final McsxElement element = new McsxElement("div", List.of(), List.of(), 1, 1);

    @Test
    void evaluatesControllerFieldsUnwrappingReactiveHolders() {
        assertEquals(7, resolver.evaluate("count", null, element));
        assertEquals("n=7", resolver.evaluate("label", null, element));
    }

    @Test
    void navigatesDottedPathsThroughRecordsAndSignals() {
        assertEquals("first", resolver.evaluate("item.name", null, element));
        assertEquals(true, resolver.evaluate("item.open", null, element));
    }

    @Test
    void scopeShadowsControllerFieldsInnermostFirst() {
        Scope outer = Scope.of("count", 100, null);
        Scope inner = Scope.of("count", 200, outer);
        assertEquals(200, resolver.evaluate("count", inner, element));
        assertEquals(100, resolver.evaluate("count", outer, element));
    }

    @Test
    void scopeValuesUnwrapSuppliersAndSignals() {
        Supplier<Object> supplier = () -> "supplied";
        assertEquals("supplied", resolver.evaluate("prop", Scope.of("prop", supplier, null), element));
        assertEquals(3, resolver.evaluate("prop", Scope.of("prop", new Signal<>(3), null), element));
    }

    @Test
    void unresolvableHeadAndNullMidPathFailWithPosition() {
        assertThrows(McsxBindException.class, () -> resolver.evaluate("nope", null, element));
        Scope holed = Scope.of("holder", null, null);
        assertThrows(McsxBindException.class, () -> resolver.evaluate("holder.x", holed, element));
    }

    @Test
    void resolveSignalReturnsTheWritableLeafOrNull() {
        assertSame(controller.count, resolver.resolveSignal("count", null, element));
        assertSame(controller.item.open(), resolver.resolveSignal("item.open", null, element));
        assertNull(resolver.resolveSignal("label", null, element), "a Computed is read-only");
    }

    @Test
    void findsAndInvokesControllerMethodsByArity() {
        resolver.invoke(resolver.findMethod("reset", 0), null);
        assertEquals("reset", controller.lastArgument);
        resolver.invoke(resolver.findMethod("remove", 1), "victim");
        assertEquals("victim", controller.lastArgument);
        assertNull(resolver.findMethod("remove", 2));
    }

    /** The B6 rule: the nearest LOOP scope wins, never a select=/state scope pushed inside it. */
    @Test
    void nearestLoopItemSkipsShadowingNonLoopScopes() {
        Scope row = Scope.item("item", "the-row", null);
        Scope selection = Scope.of("selected", (Supplier<Object>) () -> Boolean.TRUE, row);
        Scope state = Scope.of("open", new Signal<>(false), selection);

        assertSame(row, state.nearestLoopItem());
        assertEquals("the-row", BindingResolver.unwrap(state.nearestLoopItem().value()));
        assertNull(selection.parent().parent(), "chain shape sanity");
        assertNull(Scope.of("selected", true, null).nearestLoopItem(),
                "no loop in the chain means no item");
    }

    @Test
    void unwrapPeelsSignalsComputedsAndSuppliers() {
        assertEquals(7, BindingResolver.unwrap(controller.count));
        assertEquals("n=7", BindingResolver.unwrap(controller.label));
        assertEquals("x", BindingResolver.unwrap((Supplier<Object>) () -> "x"));
        assertEquals("plain", BindingResolver.unwrap("plain"));
    }
}
